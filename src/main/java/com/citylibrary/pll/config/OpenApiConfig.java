package com.citylibrary.pll.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI pllOpenApi() {
        return new OpenAPI().info(new Info()
                .title("城市图书馆跨馆借还调拨服务 API")
                .version("1.0.0")
                .description("预约排队、馆际调拨、借阅归还、逾期罚款、赔偿与异常处理、管理统计"));
    }
}
