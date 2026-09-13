package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAuthorizationService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.StaffBranchReach;
import com.exskylab.koala.core.helpers.StaffSeparationOfDuties;
import com.exskylab.koala.core.security.StaffAuditContext;
import com.exskylab.koala.core.security.StaffAuthorization;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.security.StaffRoleView;
import com.exskylab.koala.dataAccess.StaffMemberRoleDao;
import com.exskylab.koala.dataAccess.StaffRoleDao;
import com.exskylab.koala.dataAccess.StaffRoleIncludeDao;
import com.exskylab.koala.dataAccess.StaffRolePermissionDao;
import com.exskylab.koala.entities.StaffMemberRole;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffRole;
import com.exskylab.koala.entities.StaffRoleInclude;
import com.exskylab.koala.entities.StaffRolePermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StaffAuthorizationManager implements StaffAuthorizationService {

    private static final List<StaffPermission> CATALOGUE = Arrays.stream(StaffPermission.values())
            .sorted(Comparator.comparing(StaffPermission::getCode))
            .toList();

    private final StaffRoleDao staffRoleDao;
    private final StaffRolePermissionDao staffRolePermissionDao;
    private final StaffRoleIncludeDao staffRoleIncludeDao;
    private final StaffMemberRoleDao staffMemberRoleDao;

    public StaffAuthorizationManager(StaffRoleDao staffRoleDao,
                                     StaffRolePermissionDao staffRolePermissionDao,
                                     StaffRoleIncludeDao staffRoleIncludeDao,
                                     StaffMemberRoleDao staffMemberRoleDao) {
        this.staffRoleDao = staffRoleDao;
        this.staffRolePermissionDao = staffRolePermissionDao;
        this.staffRoleIncludeDao = staffRoleIncludeDao;
        this.staffMemberRoleDao = staffMemberRoleDao;
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
                    log.warn("staff_member_roles contains a role with no Koala counterpart, skipped: {}", code));
        }

        Set<UUID> closure = closureOf(seeds.stream().map(StaffRole::getId).collect(java.util.stream.Collectors.toSet()));

        return new StaffAuthorization(Set.copyOf(codesOf(closure)), permissionsOf(closure));
    }

    @Override
    public List<StaffPermission> getPermissions() {
        return CATALOGUE;
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

    @Override
    @Transactional(readOnly = true)
    public StaffRole getRoleByCode(String roleCode) {
        return staffRoleDao.findByCode(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException(StaffMessages.ROLE_NOT_FOUND));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockSeparationOfDutiesWrites() {
        staffRoleDao.lockSeparationOfDutiesWrites();
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
        for (StaffRolePermission granted : staffRolePermissionDao.findAllByRoleIdIn(roleIds)) {
            StaffPermission permission = StaffPermission.fromCode(granted.getPermissionCode()).orElse(null);
            if (permission == null) {
                log.warn("staff_role_permissions contains an unrecognized permission code, skipped: {}",
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

    private Set<UUID> rolesReaching(UUID roleId, Map<UUID, String> codeOf) {
        Set<UUID> reached = new LinkedHashSet<>();
        reached.add(roleId);
        Set<UUID> frontier = new LinkedHashSet<>(reached);

        while (!frontier.isEmpty()) {
            Set<UUID> next = new LinkedHashSet<>();
            for (StaffRoleInclude include : staffRoleIncludeDao.findAllWithRolesByChildRoleIdIn(frontier)) {
                UUID ancestor = rememberCodesOf(include, codeOf).getParentRole().getId();
                if (reached.add(ancestor)) {
                    next.add(ancestor);
                }
            }
            frontier = next;
        }
        return reached;
    }

    private Map<UUID, Set<UUID>> inclusionEdgesBelow(Collection<UUID> seedRoleIds, Map<UUID, String> codeOf) {
        Map<UUID, Set<UUID>> childrenOf = new LinkedHashMap<>();
        Set<UUID> walked = new LinkedHashSet<>(seedRoleIds);
        Set<UUID> frontier = new LinkedHashSet<>(seedRoleIds);

        while (!frontier.isEmpty()) {
            Set<UUID> next = new LinkedHashSet<>();
            for (StaffRoleInclude include : staffRoleIncludeDao.findAllWithRolesByParentRoleIdIn(frontier)) {
                UUID descendant = rememberCodesOf(include, codeOf).getChildRole().getId();
                childrenOf.computeIfAbsent(include.getParentRole().getId(), key -> new LinkedHashSet<>())
                        .add(descendant);
                if (walked.add(descendant)) {
                    next.add(descendant);
                }
            }
            frontier = next;
        }
        return childrenOf;
    }

    private static StaffRoleInclude rememberCodesOf(StaffRoleInclude include, Map<UUID, String> codeOf) {
        codeOf.put(include.getParentRole().getId(), include.getParentRole().getCode());
        codeOf.put(include.getChildRole().getId(), include.getChildRole().getCode());
        return include;
    }

    private static Set<UUID> reachedFrom(UUID rootRoleId, Map<UUID, Set<UUID>> childrenOf) {
        Set<UUID> reached = new LinkedHashSet<>();
        reached.add(rootRoleId);
        Deque<UUID> pending = new ArrayDeque<>(reached);

        while (!pending.isEmpty()) {
            for (UUID child : childrenOf.getOrDefault(pending.pop(), Set.of())) {
                if (reached.add(child)) {
                    pending.push(child);
                }
            }
        }
        return reached;
    }

    private void requireTheBranchesStaySeparateAfter(StaffRole parent, StaffRole child, Set<UUID> childClosure) {
        Map<UUID, String> codeOf = new LinkedHashMap<>();
        codeOf.put(parent.getId(), parent.getCode());
        codeOf.put(child.getId(), child.getCode());
        codeOf.putAll(roleCodesById(childClosure));

        Set<UUID> rolesReachingParent = rolesReaching(parent.getId(), codeOf);
        Map<UUID, Set<UUID>> childrenOf = inclusionEdgesBelow(rolesReachingParent, codeOf);
        Set<String> gainedThroughChild = codesOf(childClosure, codeOf);

        for (UUID role : rolesReachingParent) {
            Set<String> reachedAfterInclusion = codesOf(reachedFrom(role, childrenOf), codeOf);
            reachedAfterInclusion.addAll(gainedThroughChild);

            StaffSeparationOfDuties.requireBranchesStaySeparate(
                    StaffBranchReach.assignedRole(codeOf.get(role)).reaching(reachedAfterInclusion));
        }

        requireTheMembershipsStaySeparateAfter(rolesReachingParent, gainedThroughChild);
    }

    private void requireTheMembershipsStaySeparateAfter(Set<UUID> rolesReachingParent,
                                                       Set<String> gainedThroughChild) {
        Set<UUID> affectedMemberIds = staffMemberRoleDao.findAllByRoleIdIn(rolesReachingParent).stream()
                .map(membership -> membership.getMember().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (affectedMemberIds.isEmpty()) {
            return;
        }

        Map<UUID, String> codeOf = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> assignedRolesOf = new LinkedHashMap<>();
        for (StaffMemberRole membership : staffMemberRoleDao.findAllByMemberIdIn(affectedMemberIds)) {
            codeOf.put(membership.getRole().getId(), membership.getRole().getCode());
            assignedRolesOf.computeIfAbsent(membership.getMember().getId(), key -> new LinkedHashSet<>())
                    .add(membership.getRole().getId());
        }

        Set<UUID> everyAssignedRole = assignedRolesOf.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, Set<UUID>> childrenOf = inclusionEdgesBelow(everyAssignedRole, codeOf);

        assignedRolesOf.forEach((memberId, assignedRoles) -> {
            Set<UUID> reached = new LinkedHashSet<>();
            assignedRoles.forEach(roleId -> reached.addAll(reachedFrom(roleId, childrenOf)));

            Set<String> reachedAfterInclusion = codesOf(reached, codeOf);
            reachedAfterInclusion.addAll(gainedThroughChild);

            StaffSeparationOfDuties.requireBranchesStaySeparate(
                    StaffBranchReach.assignedRoles(codesOf(assignedRoles, codeOf))
                            .reaching(reachedAfterInclusion));
        });
    }

    private Map<UUID, String> roleCodesById(Collection<UUID> roleIds) {
        Map<UUID, String> codes = new LinkedHashMap<>();
        staffRoleDao.findAllById(roleIds).forEach(role -> codes.put(role.getId(), role.getCode()));
        return codes;
    }

    private static Set<String> codesOf(Collection<UUID> roleIds, Map<UUID, String> codeOf) {
        return roleIds.stream()
                .map(codeOf::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> codesOf(Set<UUID> roleIds) {
        return new LinkedHashSet<>(roleCodesById(roleIds).values());
    }

    private Set<StaffPermission> permissionsOf(Set<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }

        Set<StaffPermission> permissions = new HashSet<>();
        for (StaffRolePermission granted : staffRolePermissionDao.findAllByRoleIdIn(roleIds)) {
            StaffPermission permission = StaffPermission.fromCode(granted.getPermissionCode()).orElse(null);
            if (permission == null) {
                log.warn("staff_role_permissions contains an unrecognized permission code, skipped: {}", granted.getPermissionCode());
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

        String before = trailOf(role.getId());

        StaffRolePermission granted = new StaffRolePermission();
        granted.setRole(role);
        granted.setPermissionCode(permission.getCode());
        staffRolePermissionDao.save(granted);
        staffRolePermissionDao.flush();

        StaffAuditContext.capture(before, trailOf(role.getId()));
    }

    @Override
    @Transactional
    public void revokePermission(StaffPrincipal actor, UUID roleId, StaffPermission permission) {
        Set<StaffPermission> held = heldByActor(actor);
        requireGrantable(held, permission);

        StaffRole role = roleById(roleId);
        boolean managedRolesBefore = reachesRoleManagement(role.getId());
        String before = trailOf(role.getId());

        staffRolePermissionDao.deleteByRoleIdAndPermissionCode(role.getId(), permission.getCode());
        staffRolePermissionDao.flush();

        requireTheActorKeepsRoleManagement(actor, role, managedRolesBefore);
        StaffAuditContext.capture(before, trailOf(role.getId()));
    }

    @Override
    @Transactional
    public void includeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId) {
        lockSeparationOfDutiesWrites();
        Set<StaffPermission> held = heldByActor(actor);

        StaffRole parent = roleById(roleId);
        StaffRole child = roleById(includedRoleId);
        Set<UUID> childClosure = closureOf(Set.of(child.getId()));

        if (parent.getId().equals(child.getId()) || childClosure.contains(parent.getId())) {
            throw new ConflictException(StaffMessages.ROLE_GRAPH_WOULD_CYCLE);
        }

        permissionsOf(childClosure).forEach(permission -> requireGrantable(held, permission));

        if (staffRoleIncludeDao.existsByParentRoleIdAndChildRoleId(parent.getId(), child.getId())) {
            return;
        }

        requireTheBranchesStaySeparateAfter(parent, child, childClosure);

        String before = trailOf(parent.getId());

        StaffRoleInclude include = new StaffRoleInclude();
        include.setParentRole(parent);
        include.setChildRole(child);
        staffRoleIncludeDao.save(include);
        staffRoleIncludeDao.flush();

        StaffAuditContext.capture(before, trailOf(parent.getId()));
    }

    @Override
    @Transactional
    public void excludeRole(StaffPrincipal actor, UUID roleId, UUID includedRoleId) {
        Set<StaffPermission> held = heldByActor(actor);

        StaffRole parent = roleById(roleId);
        StaffRole child = roleById(includedRoleId);

        permissionsOf(closureOf(Set.of(child.getId()))).forEach(permission -> requireGrantable(held, permission));

        boolean managedRolesBefore = reachesRoleManagement(parent.getId());
        String before = trailOf(parent.getId());

        staffRoleIncludeDao.deleteByParentRoleIdAndChildRoleId(parent.getId(), child.getId());
        staffRoleIncludeDao.flush();

        requireTheActorKeepsRoleManagement(actor, parent, managedRolesBefore);
        StaffAuditContext.capture(before, trailOf(parent.getId()));
    }

    private String trailOf(UUID roleId) {
        String permissions = staffRolePermissionDao.findAllByRoleIdIn(Set.of(roleId)).stream()
                .map(StaffRolePermission::getPermissionCode)
                .sorted()
                .collect(Collectors.joining(", "));
        String includes = staffRoleIncludeDao.findAllWithRolesByParentRoleIdIn(Set.of(roleId)).stream()
                .map(include -> include.getChildRole().getCode())
                .sorted()
                .collect(Collectors.joining(", "));
        return "permissions=[" + permissions + "]; includes=[" + includes + "]";
    }

    private boolean reachesRoleManagement(UUID roleId) {
        return permissionsOf(closureOf(Set.of(roleId))).contains(StaffPermission.ROLE_MANAGE);
    }

    private void requireTheActorKeepsRoleManagement(StaffPrincipal actor, StaffRole role, boolean managedRolesBefore) {
        if (!managedRolesBefore || !actor.roles().contains(role.getCode())) {
            return;
        }
        if (!reachesRoleManagement(role.getId())) {
            throw new ConflictException(StaffMessages.ROLE_MANAGE_SELF_LOCKOUT);
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
