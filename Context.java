modelRoutingConfigurations.forEach(modelRoutingConfiguration -> {
    modelRoutingConfiguration.getConfigItems().forEach(this::processConfigItem);
});
return Mono.just(modelRoutingConfigurations);

private void processConfigItem(ConfigsItem configsItem) {
    List<ContextBuilderConfiguration> contextBuilders = getContextBuilders(configsItem);
    if (contextBuilders.isEmpty()) {
        return;
    }
    List<ModelsDto> models = getModels(configsItem);
    if (models.isEmpty()) {
        return;
    }
    Map<String, ContextBuilderConfiguration> finalContextBuildersMap = new HashMap<>();
    for (ModelsDto model : models) {
        List<ContextBuilderConfiguration> finalContexts =
            contextBuilderUtil.populateContextBuildersAndReturn(contextBuilders, model.getNodeId());
        if (finalContexts != null && !finalContexts.isEmpty()) {
            finalContexts.forEach(contextBuilderConfiguration ->
                finalContextBuildersMap.putIfAbsent(contextBuilderConfiguration.getKey(), contextBuilderConfiguration));
        }
    }
    if (!finalContextBuildersMap.isEmpty()) {
        AudienceDomain audience = configsItem.getAudience();
        if (audience == null) {
            audience = new AudienceDomain();
            configsItem.setAudience(audience);
        }
        List<ContextBuilderConfiguration> audienceContextBuilders = audience.getContextBuilder();
        if (audienceContextBuilders == null) {
            audienceContextBuilders = new ArrayList<>();
            audience.setContextBuilder(audienceContextBuilders);
        }
        audienceContextBuilders.addAll(finalContextBuildersMap.values());
    }
}

private List<ModelsDto> getModels(ConfigsItem configsItem) {
    if (configsItem.getRoutes() != null && configsItem.getRoutes().getModels() != null) {
        return configsItem.getRoutes().getModels();
    } else if (configsItem.getExperiments() != null) {
        return configsItem.getExperiments().stream()
            .flatMap(experimentGroupConfig -> experimentGroupConfig.getModels().stream())
            .collect(Collectors.toList());
    } else {
        return Collections.emptyList();
    }
}

private List<ContextBuilderConfiguration> getContextBuilders(ConfigsItem configsItem) {
    AudienceDomain audience = configsItem.getAudience();
    return (audience != null && audience.getContextBuilder() != null) ? audience.getContextBuilder() : Collections.emptyList();
}
