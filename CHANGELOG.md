# CHANGELOG
All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). See the [CONTRIBUTING guide](./CONTRIBUTING.md#Changelog) for instructions on how to add changelog entries.

## [Unreleased 3.0](https://github.com/opensearch-project/neural-search/compare/2.x...HEAD)
### Features
### Enhancements
### Bug Fixes
### Infrastructure
### Documentation
### Maintenance
### Refactoring

## [Unreleased 2.x](https://github.com/opensearch-project/neural-search/compare/2.15...2.x)
### Features
- Enable sorting and search_after features in Hybrid Search [#827](https://github.com/opensearch-project/neural-search/pull/827)
### Enhancements
- InferenceProcessor inherits from AbstractBatchingProcessor to support sub batching in processor [#820](https://github.com/opensearch-project/neural-search/pull/820)
- Adds dynamic knn query parameters efsearch and nprobes [#814](https://github.com/opensearch-project/neural-search/pull/814/)
- Enable '.' for nested field in text embedding processor ([#811](https://github.com/opensearch-project/neural-search/pull/811))
### Bug Fixes
- Fix for missing HybridQuery results when concurrent segment search is enabled ([#800](https://github.com/opensearch-project/neural-search/pull/800))
### Infrastructure
- Add BWC for batch ingestion ([#769](https://github.com/opensearch-project/neural-search/pull/769))
- Add backward test cases for neural sparse two phase processor ([#777](https://github.com/opensearch-project/neural-search/pull/777))
- Fix CI for JDK upgrade towards 21 ([#835](https://github.com/opensearch-project/neural-search/pull/835))
- Maven publishing workflow by upgrade jdk to 21 ([#837](https://github.com/opensearch-project/neural-search/pull/837))
### Documentation
### Maintenance
### Refactoring
