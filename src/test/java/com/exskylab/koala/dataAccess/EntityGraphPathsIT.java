package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.support.Repositories;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityGraphPathsIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ListableBeanFactory beanFactory;

    @Test
    void everyEntityGraphPathResolvesAgainstTheMetamodel() {
        Metamodel metamodel = entityManager.getMetamodel();
        Repositories repositories = new Repositories(beanFactory);
        List<String> failures = new ArrayList<>();
        int checked = 0;

        for (Class<?> domainType : repositories) {
            Class<?> repositoryInterface = repositories.getRequiredRepositoryInformation(domainType)
                    .getRepositoryInterface();

            for (Method method : repositoryInterface.getDeclaredMethods()) {
                EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);
                if (entityGraph == null) {
                    continue;
                }
                for (String path : entityGraph.attributePaths()) {
                    checked++;
                    String problem = resolve(metamodel, domainType, path);
                    if (problem != null) {
                        failures.add("%s.%s -> \"%s\": %s".formatted(
                                repositoryInterface.getSimpleName(), method.getName(), path, problem));
                    }
                }
            }
        }

        assertTrue(checked > 0, "Hic @EntityGraph bulunamadi - test yanlis yeri tariyor olabilir.");
        assertTrue(failures.isEmpty(), "Cozulemeyen entity graph yollari:\n  " + String.join("\n  ", failures));
    }

    private String resolve(Metamodel metamodel, Class<?> rootType, String path) {
        ManagedType<?> current = metamodel.managedType(rootType);
        String[] segments = path.split("\\.");

        for (int i = 0; i < segments.length; i++) {
            Attribute<?, ?> attribute;
            try {
                attribute = current.getAttribute(segments[i]);
            } catch (IllegalArgumentException e) {
                return "'%s' alani %s uzerinde yok".formatted(segments[i], current.getJavaType().getSimpleName());
            }

            if (i == segments.length - 1) {
                return null;
            }

            Type<?> next = attribute instanceof PluralAttribute<?, ?, ?> plural
                    ? plural.getElementType()
                    : ((SingularAttribute<?, ?>) attribute).getType();

            if (!(next instanceof ManagedType<?> managed)) {
                return "'%s' bir deger tipi, altinda '%s' aranamaz".formatted(segments[i], segments[i + 1]);
            }
            current = managed;
        }
        return null;
    }
}
