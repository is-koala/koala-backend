package com.exskylab.koala.core.configs;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.query.sqm.produce.function.FunctionParameterType;
import org.hibernate.type.StandardBasicTypes;

public class UnaccentFunctionContributor implements FunctionContributor {

    public static final String FUNCTION_NAME = "unaccent";

    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        functionContributions.getFunctionRegistry()
                .namedDescriptorBuilder(FUNCTION_NAME)
                .setInvariantType(functionContributions.getTypeConfiguration()
                        .getBasicTypeRegistry().resolve(StandardBasicTypes.STRING))
                .setExactArgumentCount(1)
                .setParameterTypes(FunctionParameterType.STRING)
                .register();
    }
}
