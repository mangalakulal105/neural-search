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

## [Unreleased 2.x](https://github.com/opensearch-project/neural-search/compare/2.14...2.x)
### Features
- Speed up NeuralSparseQuery by two-phase using a custom search pipeline.([#646](https://github.com/opensearch-project/neural-search/issues/646))
- Support batchExecute in TextEmbeddingProcessor and SparseEncodingProcessor ([#743](https://github.com/opensearch-project/neural-search/issues/743))
### Enhancements
- Pass empty doc collector instead of top docs collector to improve hybrid query latencies by 20% ([#731](https://github.com/opensearch-project/neural-search/pull/731))
- Optimize parameter parsing in text chunking processor ([#733](https://github.com/opensearch-project/neural-search/pull/733))
- Use lazy initialization for priority queue of hits and scores to improve latencies by 20% ([#746](https://github.com/opensearch-project/neural-search/pull/746))
- Optimize max score calculation in the Query Phase of the Hybrid Search ([765](https://github.com/opensearch-project/neural-search/pull/765))
### Bug Fixes
- Total hit count fix in Hybrid Query ([756](https://github.com/opensearch-project/neural-search/pull/756))
### Infrastructure
- Disable memory circuit breaker for integ tests ([#770](https://github.com/opensearch-project/neural-search/pull/770))
### Documentation
### Maintenance
### Refactoring
