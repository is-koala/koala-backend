package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.PluralAttribute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.support.Repositories;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EagerFetchCoverageIT extends AbstractIntegrationTest {

    private static final Map<String, String> LAZY_COMPANIONS = Map.of(
            "User", "profilePicture",
            "Badge", "icon",
            "Company", "logo");

    private static final Map<String, String> EXEMPTIONS = Map.ofEntries(
            entry("CompanyFollowDao.findAllByCompanyId", "follower",
                    "bildirim sorgusu; takipçinin fotoğrafı hiçbir yanıtta çizilmiyor"),
            entry("JobDao.findAllByIdIn", "creator", "JobSummaryDto ilan sahibini çizmiyor"),
            entry("JobDao.findAllByIdIn", "company", "JobSummaryDto yalnızca companyName taşıyor"),
            entry("JobDao.findAllByCreatorId", "company", "JobSummaryDto yalnızca companyName taşıyor"),
            entry("JobDao.findAllByCompanyId", "company", "JobSummaryDto yalnızca companyName taşıyor"),
            entry("JobAssignmentDao.findAllByWorkerId", "job.company", "JobSummaryDto yalnızca companyName taşıyor"),
            entry("JobAssignmentDao.findAllByJobCreatorId", "job.company", "JobSummaryDto yalnızca companyName taşıyor"),
            entry("JobAssignmentDao.findAllForStaff", "job.company",
                    "AdminAssignmentDto yalnızca companyName taşıyor"),
            entry("JobApplicationDao.findAllByUserId", "job.company", "JobSummaryDto yalnızca companyName taşıyor"),
            entry("JobApplicationDao.findAllByUserIdAndStatus", "job.company", "JobSummaryDto yalnızca companyName taşıyor"),
            entry("JobApplicationDao.findReceivedPendingFirst", "job.company",
                    "ReceivedApplicationDto yalnızca companyId ve companyName taşıyor"),
            entry("JobApplicationDao.findReceived", "job.company",
                    "ReceivedApplicationDto yalnızca companyId ve companyName taşıyor"),
            entry("TalentPoolEntryDao.findAllByEmployerId", "company", "TalentPoolEntryDto yalnızca companyId taşıyor"),
            entry("JobTemplateDao.findAllByOwnerIdOrderByCreatedAtDesc", "company", "JobTemplateDto yalnızca companyId taşıyor"),
            entry("PayoutDao.findAllByWorkerIdOrderByCreatedAtDesc", "worker", "PayoutDto işçiyi çizmiyor"),
            entry("JobAlertDao.findAllByActiveTrue", "user", "kullanıcı yalnızca bildirim alıcısı, yanıta girmiyor"),
            entry("IdentityVerificationDao.findById", "user",
                    "IdentityReviewDto yalnızca userId taşıyor"),
            entry("IdentityVerificationDao.search", "user",
                    "IdentityReviewDto yalnızca userId taşıyor"));

    private static Map.Entry<String, String> entry(String method, String path, String reason) {
        return Map.entry(method + "#" + path, reason);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ListableBeanFactory beanFactory;

    private record GraphUse(String repository, String method, List<String> paths, boolean returnsCollection) {

        String key() {
            return repository + "." + method;
        }
    }

    private static boolean returnsCollection(Method method) {
        Class<?> type = method.getReturnType();
        return Collection.class.isAssignableFrom(type)
                || Page.class.isAssignableFrom(type)
                || Slice.class.isAssignableFrom(type);
    }

    private List<GraphUse> declaredGraphs() {
        Repositories repositories = new Repositories(beanFactory);
        List<GraphUse> uses = new ArrayList<>();

        for (Class<?> domainType : repositories) {
            Class<?> repositoryInterface = repositories.getRequiredRepositoryInformation(domainType)
                    .getRepositoryInterface();

            for (Method method : repositoryInterface.getDeclaredMethods()) {
                EntityGraph graph = method.getAnnotation(EntityGraph.class);
                if (graph != null && graph.attributePaths().length > 0) {
                    uses.add(new GraphUse(repositoryInterface.getSimpleName(), method.getName(),
                            List.of(graph.attributePaths()), returnsCollection(method)));
                }
            }
        }
        return uses;
    }

    private String targetEntityOf(EntityType<?> root, String path) {
        Object current = root;

        for (String segment : path.split("\\.")) {
            if (!(current instanceof ManagedType<?> managed)) {
                return null;
            }

            Attribute<?, ?> attribute;
            try {
                attribute = managed.getAttribute(segment);
            } catch (IllegalArgumentException e) {
                return null;
            }

            if (!attribute.isAssociation()) {
                return null;
            }

            Class<?> javaType = attribute instanceof PluralAttribute<?, ?, ?> plural
                    ? plural.getElementType().getJavaType()
                    : attribute.getJavaType();
            current = entityManager.getMetamodel().entity(javaType);
        }

        return current instanceof EntityType<?> entity ? entity.getName() : null;
    }

    @Test
    void aCollectionQueryThatFetchesAnEntityAlsoFetchesTheLazyPartItsDtoRenders() {
        Repositories repositories = new Repositories(beanFactory);
        Map<String, EntityType<?>> roots = new HashMap<>();

        for (Class<?> domainType : repositories) {
            String name = repositories.getRequiredRepositoryInformation(domainType)
                    .getRepositoryInterface().getSimpleName();
            roots.put(name, entityManager.getMetamodel().entity(domainType));
        }

        List<String> failures = new ArrayList<>();

        for (GraphUse use : declaredGraphs()) {
            if (!use.returnsCollection()) {
                continue;
            }

            EntityType<?> root = roots.get(use.repository());
            if (root == null) {
                continue;
            }

            for (String path : use.paths()) {
                String target = targetEntityOf(root, path);
                String companion = target == null ? null : LAZY_COMPANIONS.get(target);

                if (companion == null || EXEMPTIONS.containsKey(use.key() + "#" + path)) {
                    continue;
                }

                String expected = path + "." + companion;
                if (!use.paths().contains(expected)) {
                    failures.add(("%s.%s liste döndürüyor ve '%s' ile bir %s çekiyor, "
                            + "ama '%s' eksik. Yanıt onu çiziyorsa satır başına bir sorgu daha atılır; "
                            + "çizmiyorsa EXEMPTIONS listesine gerekçesiyle eklenmeli.")
                            .formatted(use.repository(), use.method(), path, target, expected));
                }
            }
        }

        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void everyExemptionStillPointsAtALiveGraph() {
        Set<String> live = new HashSet<>();
        for (GraphUse use : declaredGraphs()) {
            for (String path : use.paths()) {
                live.add(use.key() + "#" + path);
            }
        }

        List<String> stale = EXEMPTIONS.keySet().stream()
                .filter(key -> !live.contains(key))
                .sorted()
                .toList();

        assertTrue(stale.isEmpty(), "artık var olmayan bir graph için muafiyet duruyor: " + stale);
    }

    @Test
    void theAuditIsActuallyLookingAtSomething() {
        assertTrue(declaredGraphs().stream().filter(GraphUse::returnsCollection).count() > 10,
                "liste döndüren entity graph taraması boş, denetim bir şey doğrulamıyor");
    }
}
