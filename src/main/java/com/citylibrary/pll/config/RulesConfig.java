package com.citylibrary.pll.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RulesProperties.class)
public class RulesConfig {
}
