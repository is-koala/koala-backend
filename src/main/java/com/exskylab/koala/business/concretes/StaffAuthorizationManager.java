package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuthorizationService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.security.StaffAuthorization;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.security.StaffRoleView;
import com.exskylab.koala.dataAccess.StaffMemberDao;
import com.exskylab.koala.dataAccess.StaffRoleDao;
import com.exskylab.koala.dataAccess.StaffRoleIncludeDao;
import com.exskylab.koala.dataAccess.StaffRolePermissionDao;
import com.exskylab.koala.entities.StaffMember;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffRole;
import com.exskylab.koala.entities.StaffRoleInclude;
import com.exskylab.koala.entities.StaffRolePermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class StaffAuthorizationManager implements StaffAuthorizationService {

    private final StaffMemberDao staffMemberDao;
    private final StaffRoleDao staffRoleDao;
    private final StaffRolePermissionDao staffRolePermissionDao;
    private final StaffRoleIncludeDao staffRoleIncludeDao;

    public StaffAuthorizationManager(StaffMemberDao staffMemberDao, StaffRoleDao staffRoleDao,
                                     StaffRolePermissionDao staffRolePermissionDao,
                                     StaffRoleIncludeDao staffRoleIncludeDao) {
        this.staffMemberDao = staffMemberDao;
        this.staffRoleDao = staffRoleDao;
        this.staffRolePermissionDao = staffRolePermissionDao;
        this.staffRoleIncludeDao = staffRoleIncludeDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffMember> findActiveMember(String keycloakSubject) {
        if (keycloakSubject == null || keycloakSubject.isBlank()) {
            return Optional.empty();
        }
        return staffMemberDao.findByKeycloakSubjectAndActiveTrue(keycloakSubject);
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAuthorization resolve(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return StaffAuthorization.empty();
        }

        List<StaffRole> seeds = staffRoleDao.findAllByCodeIn(Set.copyOf(roleCodes));
        if (seeds.size() < roleCodes.size()) {
            Set<String> known = seeds.stream().map(StaffRole::getCode).collect(java.util.stream.Collectors.toSet());
            roleCodes.stream().filter(code -> !known.contains(code)).forEach(code ->
                    log.warn("Keycloak token'ında Koala'da karşılığı olmayan rol var, atlandı: {}", code));
        }

        Set<UUID> closure = closureOf(seeds.stream().map(StaffRole::getId).collect(java.util.stream.Collectors.toSet()));
        Set<String> roles = new LinkedHashSet<>();
        staffRoleDao.findAllById(closure).forEach(role -> roles.add(role.getCode()));

        return new StaffAuthorization(Set.copyOf(roles), permissionsOf(closure));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffRoleView> getRoles() {
        return List.copyOf(graph().values());
    }

    @Override
    @Transactional(readOnly = true)
    public StaffRoleView getRole(UUID roleId) {
        StaffRoleView view = graph().get(roleId);
        if (view == null) {
            throw new ResourceNotFoundException(StaffMessages.ROLE_NOT_FOUND);
        }
        return view;
    }

    private Map<UUID, StaffRoleView> graph() {
        List<StaffRole> roles = staffRoleDao.findAll(Sort.by(Sort.Direction.ASC, "code"));
        Set<UUID> roleIds = roles.stream().map(StaffRole::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, List<StaffRole>> childrenOf = childrenOf(roleIds);
        Map<UUID, Set<StaffPermission>> directOf = directPermissionsOf(roleIds);

        Map<UUID, StaffRoleView> views = new LinkedHashMap<>();
        for (StaffRole role : roles) {
            List<StaffRole> included = includedBelow(role, childrenOf);
            Set<StaffPermission> effective = new LinkedHashSet<>(
                    directOf.getOrDefault(role.getId(), Set.of()));
            included.forEach(reached -> effective.addAll(directOf.getOrDefault(reached.getId(), Set.of())));

            views.put(role.getId(), new StaffRoleView(role,
                    Set.copyOf(directOf.getOrDefault(role.getId(), Set.of())),
                    List.copyOf(childrenOf.getOrDefault(role.getId(), List.of())),
                    Set.copyOf(effective),
                    included));
        }
        return views;
    }

    private Map<UUID, List<StaffRole>> childrenOf(Set<UUID> roleIds) {
        Map<UUID, List<StaffRole>> childrenOf = new LinkedHashMap<>();
        if (roleIds.isEmpty()) {
            return childrenOf;
        }
        for (StaffRoleInclude include : staffRoleIncludeDao.findAllWithRolesByParentRoleIdIn(roleIds)) {
            childrenOf.computeIfAbsent(include.getParentRole().getId(), key -> new ArrayList<>())
                    .add(include.getChildRole());
        }
        childrenOf.values().forEach(children -> children.sort(Comparator.comparing(StaffRole::getCode)));
        return childrenOf;
    }

    private Map<UUID, Set<StaffPermission>> directPermissionsOf(Set<UUID> roleIds) {
        Map<UUID, Set<StaffPermission>> directOf = new LinkedHashMap<>();
        if (roleIds.isEmpty()) {
            return directOf;
        }
        for (StaffRolePermission granted : staffRolePermissionDao.findAllWithRoleByRoleIdIn(roleIds)) {
            StaffPermission permission = StaffPermission.fromCode(granted.getPermissionCode()).orElse(null);
            if (permission == null) {
                log.warn("staff_role_permissions içinde tanınmayan izin kodu, atlandı: {}",
                        granted.getPermissionCode());
                continue;
            }
            directOf.computeIfAbsent(granted.getRole().getId(), key -> new LinkedHashSet<>()).add(permission);
        }
        return directOf;
    }

    private static List<StaffRole> includedBelow(StaffRole root, Map<UUID, List<StaffRole>> childrenOf) {
        Map<UUID, StaffRole> reached = new LinkedHashMap<>();
        List<StaffRole> frontier = new ArrayList<>(childrenOf.getOrDefault(root.getId(), List.of()));

        while (!frontier.isEmpty()) {
            List<StaffRole> next = new ArrayList<>();
            for (StaffRole role : frontier) {
                if (role.getId().equals(root.getId()) || reached.putIfAbsent(role.getId(), role) != null) {
                    continue;
                }
                next.addAll(childrenOf.getOrDefault(role.getId(), List.of()));
            }
            frontier = next;
        }

        return reached.values().stream().sorted(Comparator.comparing(StaffRole::getCode)).toList();
    }

    private Set<UUID> closureOf(Set<UUID> seedRoleIds) {
        Set<UUID> reached = new LinkedHashSet<>(seedRoleIds);
        Set<UUID> frontier = new LinkedHashSet<>(seedRoleIds);

        while (!frontier.isEmpty()) {
            Set<UUID> next = new LinkedHashSet<>();
            for (StaffRoleInclude include : staffRoleIncludeDao.findAllByParentRoleIdIn(frontier)) {
                UUID child = include.getChildRole().getId();
                if (reached.add(child)) {
                    next.add(child);
                }
            }
            frontier = next;
        }
        return reached;
    }

    private Set<StaffPermission> permissionsOf(Set<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }

        Set<StaffPermission> permissions = new HashSet<>();
        for (StaffRolePermission granted : staffRolePermissionDao.findAllByRoleIdIn(roleIds)) {
            StaffPermission permission = StaffPermission.fromCode(granted.getPermissionCode()).orElse(null);
            if (permission == null) {
                log.warn("staff_role_permissions içinde tanınmayan izin kodu, atlandı: {}", granted.getPermissionCode());
                continue;
            }
            permissions.add(permission);
        }
        return Set.copyOf(permissions);
    }

    @Override
    @Transactional
    public void grantPermission(StaffPrincipal actor, UUID roleId, StaffPermission permission) {
        Set<StaffPermission> held = heldByActor(actor);
        requireGrantable(held, permission);

        StaffRole role = roleById(roleId);
        if (staffRolePermissionDao.existsByRoleIdAndPermissionCode(role.getId(), permission.getCode())) {
            return;
        }

        StaffRolePermission granted = new StaffRolePermission();
        granted.setRole(role);
        granted.setPermissionCode(permission.getCode());
        staffRolePermissionDao.save(granted);
    }

    @Override
    @Transactional
    public void revokePermission(StaffPrincipal actor, UUID roleId, StaffPermission permission) {
        Set<StaffPermission> held = heldByActor(actor);
        requireGrantable(held, permission);

        StaffRole role = roleById(roleId);
        boolean managedRolesBefore = managesRoles(role.getId());

        staffRolePermissionDao.deleteByRoleIdAndPermissionCode(role.getId(), permission.getCode());
        staffRolePermissionDao.flush();

        requireRoleManagementSurvives(actor, role, managedRolesBefore);
    }

    @Override
    @Transactional
    public void includeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId) {
        Set<StaffPermission> held = heldByActor(actor);

        StaffRole parent = roleById(roleId);
        StaffRole child = roleById(includedRoleId);

        if (parent.getId().equals(child.getId()) || closureOf(Set.of(child.getId())).contains(parent.getId())) {
            throw new ConflictException(StaffMessages.ROLE_GRAPH_WOULD_CYCLE);
        }

        permissionsOf(closureOf(Set.of(child.getId()))).forEach(permission -> requireGrantable(held, permission));

        if (staffRoleIncludeDao.existsByParentRoleIdAndChildRoleId(parent.getId(), child.getId())) {
            return;
        }

        StaffRoleInclude include = new StaffRoleInclude();
        include.setParentRole(parent);
        include.setChildRole(child);
        staffRoleIncludeDao.save(include);
    }

    @Override
    @Transactional
    public void excludeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId) {
        Set<StaffPermission> held = heldByActor(actor);

        StaffRole parent = roleById(roleId);
        StaffRole child = roleById(includedRoleId);

        permissionsOf(closureOf(Set.of(child.getId()))).forEach(permission -> requireGrantable(held, permission));

        boolean managedRolesBefore = managesRoles(parent.getId());

        staffRoleIncludeDao.deleteByParentRoleIdAndChildRoleId(parent.getId(), child.getId());
        staffRoleIncludeDao.flush();

        requireRoleManagementSurvives(actor, parent, managedRolesBefore);
    }

    private boolean managesRoles(UUID roleId) {
        return permissionsOf(closureOf(Set.of(roleId))).contains(StaffPermission.ROLE_MANAGE);
    }

    private void requireRoleManagementSurvives(StaffPrincipal actor, StaffRole role, boolean managedRolesBefore) {
        if (!managedRolesBefore || !actor.roles().contains(role.getCode())) {
            return;
        }
        if (!managesRoles(role.getId())) {
            throw new ConflictException(StaffMessages.ROLE_MANAGE_LAST_HOLDER);
        }
    }

    private Set<StaffPermission> heldByActor(StaffPrincipal actor) {
        if (actor == null || !actor.isRegistered()) {
            throw new ForbiddenException(StaffMessages.PERMISSION_NOT_GRANTABLE);
        }
        return resolve(actor.roles()).permissions();
    }

    private void requireGrantable(Set<StaffPermission> held, StaffPermission permission) {
        if (!held.contains(StaffPermission.ROLE_MANAGE) || !held.contains(permission)) {
            throw new ForbiddenException(StaffMessages.PERMISSION_NOT_GRANTABLE);
        }
    }

    private StaffRole roleById(UUID roleId) {
        return staffRoleDao.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(StaffMessages.ROLE_NOT_FOUND));
    }
}
