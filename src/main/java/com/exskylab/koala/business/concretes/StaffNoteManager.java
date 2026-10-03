package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.StaffAccountService;
import com.exskylab.koala.business.abstracts.AuditSubjectService;
import com.exskylab.koala.business.abstracts.StaffNoteService;
import com.exskylab.koala.core.constants.StaffMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.Concealment;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.security.StaffAuditContext;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.dataAccess.StaffFlagDao;
import com.exskylab.koala.dataAccess.StaffNoteDao;
import com.exskylab.koala.entities.StaffFlag;
import com.exskylab.koala.entities.StaffFlagKind;
import com.exskylab.koala.entities.StaffNote;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.StaffResourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class StaffNoteManager implements StaffNoteService {

    private static final Set<String> SORTABLE = Set.of("createdAt");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final StaffNoteDao staffNoteDao;
    private final StaffFlagDao staffFlagDao;
    private final StaffAccountService staffAccountService;
    private final PageableSanitizer pageableSanitizer;
    private final AuditSubjectService auditSubjectService;

    public StaffNoteManager(StaffNoteDao staffNoteDao, StaffFlagDao staffFlagDao,
                            StaffAccountService staffAccountService, PageableSanitizer pageableSanitizer,
                            AuditSubjectService auditSubjectService) {
        this.staffNoteDao = staffNoteDao;
        this.staffFlagDao = staffFlagDao;
        this.staffAccountService = staffAccountService;
        this.pageableSanitizer = pageableSanitizer;
        this.auditSubjectService = auditSubjectService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StaffNote> listNotes(StaffResourceType resourceType, UUID resourceId, Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, SORTABLE);
        Pageable page = pageableSanitizer.sanitize(pageable, SORTABLE, NEWEST_FIRST);
        return staffNoteDao.findAllByResourceTypeAndResourceId(resourceType, resourceId, page);
    }

    @Override
    @Transactional
    public StaffNote createNote(StaffPrincipal actor, StaffResourceType resourceType, UUID resourceId,
                                String body) {
        StaffNote note = new StaffNote();
        note.setResourceType(resourceType);
        note.setResourceId(resourceId);
        note.setAuthor(staffAccountService.getReference(actor.staffId()));
        note.setBody(body.trim());

        StaffNote saved = staffNoteDao.save(note);
        StaffAuditContext.capture(null, snapshotOf(saved));
        stampSubjectOf(resourceType, resourceId);
        log.info("Staff note {} created on {} {} by staff {}", saved.getId(), resourceType, resourceId,
                actor.staffId());
        return saved;
    }

    @Override
    @Transactional
    public StaffNote updateNote(StaffPrincipal actor, UUID noteId, String body) {
        StaffNote note = editableNote(actor, noteId);
        String before = snapshotOf(note);

        note.setBody(body.trim());
        note.setEditedAt(Instant.now());
        StaffNote saved = staffNoteDao.save(note);

        StaffAuditContext.capture(before, snapshotOf(saved));
        stampSubjectOf(saved.getResourceType(), saved.getResourceId());
        log.info("Staff note {} edited by staff {}", noteId, actor.staffId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteNote(StaffPrincipal actor, UUID noteId) {
        StaffNote note = editableNote(actor, noteId);

        StaffAuditContext.capture(snapshotOf(note), null);
        stampSubjectOf(note.getResourceType(), note.getResourceId());
        staffNoteDao.delete(note);
        log.info("Staff note {} deleted by staff {}", noteId, actor.staffId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StaffFlag> listActiveFlags(StaffResourceType resourceType, Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return List.of();
        }
        return staffFlagDao.findAllByResourceTypeAndResourceIdInAndClearedAtIsNullOrderBySetAtDesc(
                resourceType, resourceIds);
    }

    @Override
    @Transactional
    public StaffFlag setFlag(StaffPrincipal actor, StaffResourceType resourceType, UUID resourceId,
                             StaffFlagKind kind, String label) {
        String trimmedLabel = label == null || label.isBlank() ? null : label.trim();
        String before = staffFlagDao
                .findByResourceTypeAndResourceIdAndKindAndClearedAtIsNull(resourceType, resourceId, kind)
                .map(StaffNoteManager::snapshotOf)
                .orElse(null);

        staffFlagDao.upsertActive(UUID.randomUUID(), resourceType.name(), resourceId, kind.name(), trimmedLabel,
                staffAccountService.getReference(actor.staffId()).getId(), Instant.now());
        StaffFlag saved = staffFlagDao
                .findByResourceTypeAndResourceIdAndKindAndClearedAtIsNull(resourceType, resourceId, kind)
                .orElseThrow(() -> new ResourceNotFoundException(StaffMessages.FLAG_NOT_FOUND));

        StaffAuditContext.capture(before, snapshotOf(saved));
        stampSubjectOf(resourceType, resourceId);
        log.info("Staff flag {} {} set on {} {} by staff {}", saved.getId(), kind, resourceType, resourceId,
                actor.staffId());
        return saved;
    }

    @Override
    @Transactional
    public void clearFlag(StaffPrincipal actor, StaffResourceType resourceType, UUID resourceId,
                          StaffFlagKind kind) {
        StaffFlag flag = staffFlagDao
                .findByResourceTypeAndResourceIdAndKindAndClearedAtIsNull(resourceType, resourceId, kind)
                .orElseThrow(() -> new ResourceNotFoundException(StaffMessages.FLAG_NOT_FOUND));
        String before = snapshotOf(flag);

        flag.setClearedAt(Instant.now());
        StaffFlag saved = staffFlagDao.save(flag);

        StaffAuditContext.capture(before, snapshotOf(saved));
        stampSubjectOf(resourceType, resourceId);
        log.info("Staff flag {} {} cleared on {} {} by staff {}", saved.getId(), kind, resourceType, resourceId,
                actor.staffId());
    }

    private void stampSubjectOf(StaffResourceType resourceType, UUID resourceId) {
        StaffAuditContext.about(auditSubjectService.forStaffTarget(resourceType, resourceId));
    }

    private StaffNote editableNote(StaffPrincipal actor, UUID noteId) {
        StaffNote note = staffNoteDao.findWithAuthorById(noteId)
                .orElseThrow(() -> new ResourceNotFoundException(StaffMessages.NOTE_NOT_FOUND));

        boolean author = note.getAuthor().getId().equals(actor.staffId());
        if (!author && !actor.holds(StaffPermission.STAFF_MANAGE)) {
            throw Concealment.notFound(StaffMessages.NOTE_NOT_FOUND, actor.staffId(), noteId,
                    "staff is neither the author of the note nor holds staff.manage");
        }
        return note;
    }

    private static String snapshotOf(StaffNote note) {
        return note.getResourceType() + "/" + note.getResourceId() + " note=" + note.getId()
                + " body=" + note.getBody();
    }

    private static String snapshotOf(StaffFlag flag) {
        return flag.getResourceType() + "/" + flag.getResourceId() + " kind=" + flag.getKind()
                + " label=" + flag.getLabel() + " active=" + flag.isActive();
    }
}
