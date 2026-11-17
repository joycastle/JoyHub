package com.iflytek.skillhub.dto;

/** Shared resource identity returned by MCP and CLI discovery. Catalog resources have no Skill coordinate. */
public record ResourceDiscoveryItem(String resourceId, String kind, String name, String summary,
                                    String accessMode, String slug, String namespace, String version) { }
