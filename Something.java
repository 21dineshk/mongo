@Override
public Mono<GetImpactedConfigsResponse> getImpactedConfigs(ApiV1Experiment experiment, String token) {
    return getExperimentById(experiment.getExperimentId(), token)
        .flatMap(retrieved -> {
            if (retrieved.getExperimentId() == null || retrieved.getExperimentId().isEmpty()) {
                return Mono.just(buildCreateOnlyResponse(experiment));
            }

            if (!compareContent(retrieved).test(experiment)) {
                return Mono.just(buildNoChangeResponse(retrieved));
            }

            ApiV1Experiment updatedExp = experiment.toBuilder().setVersion(retrieved.getVersion()).build();
            return processExperiment(updatedExp, token);
        });
}


private Mono<GetImpactedConfigsResponse> processExperiment(ApiV1Experiment experiment, String token) {
    return getModelIds(experiment)
        .flatMap(modelIds -> traverseAudience(experiment.getAudience(), modelIds))
        .flatMapMany(models -> Flux.fromIterable(models)
            .parallel()
            .runOn(Schedulers.boundedElastic())  // Use elastic thread pool for I/O
            .flatMap(model -> getModelConfiguration(model, token)
                .map(cfg -> model.toBuilder().setConfiguration(cfg).build()))
            .sequential()
        )
        .flatMap(models -> Flux.fromIterable(models)
            .flatMap(model -> getImpactedComponentsAndModels(model, token))
            .collectList()
        )
        .map(this::buildFinalResponse);
}


private Mono<List<String>> getModelIds(ApiV1Experiment exp) {
    return Mono.fromSupplier(() ->
        Optional.ofNullable(exp.getComponents()).orElse(Collections.emptyList())
            .stream()
            .flatMap(c -> Optional.ofNullable(c.getMsgConfig()).stream())
            .flatMap(cfg -> Optional.ofNullable(cfg.getVariations()).orElse(Collections.emptyList()).stream())
            .flatMap(v -> Optional.ofNullable(v.getModels()).orElse(Collections.emptyList()).stream())
            .map(Model::getModelId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList())
    );


private GetImpactedConfigsResponse buildFinalResponse(List<GetImpactedComponentsAndModels> responses) {
    Map<String, ApiV1ModelConfiguration> create = new HashMap<>();
    Map<String, ApiV1ModelConfiguration> update = new HashMap<>();
    Map<String, ApiV1ModelConfiguration> noChange = new HashMap<>();
    Map<String, ApiV1ModelConfiguration> createModel = new HashMap<>();
    Map<String, ApiV1ModelConfiguration> updateModel = new HashMap<>();
    Map<String, ApiV1ModelConfiguration> noChangeModel = new HashMap<>();

    for (var r : responses) {
        putAll(r.getCreateComponentsList(), create);
        putAll(r.getUpdateComponentsList(), update);
        putAll(r.getNoChangeComponentsList(), noChange);
        putAll(r.getCreateModelsList(), createModel);
        putAll(r.getUpdateModelsList(), updateModel);
        putAll(r.getNoChangeModelsList(), noChangeModel);
    }

    return GetImpactedConfigsResponse.builder()
        .createComponentsList(List.copyOf(create.values()))
        .updateComponentsList(List.copyOf(update.values()))
        .noChangeComponentsList(List.copyOf(noChange.values()))
        .createModelsList(List.copyOf(createModel.values()))
        .updateModelsList(List.copyOf(updateModel.values()))
        .noChangeModelsList(List.copyOf(noChangeModel.values()))
        .build();
}

private void putAll(List<ApiV1ModelConfiguration> list, Map<String, ApiV1ModelConfiguration> map) {
    if (list != null && !list.isEmpty()) {
        for (ApiV1ModelConfiguration item : list) {
            map.put(item.getModelId(), item);
        }
    }
}

