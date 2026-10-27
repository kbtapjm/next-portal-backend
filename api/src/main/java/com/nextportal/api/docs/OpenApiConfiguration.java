package com.nextportal.api.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "portal.docs.enabled", havingValue = "true")
public class OpenApiConfiguration {
    @Bean
    OpenAPI portalOpenApi(@Value("${portal.docs.title:${spring.application.name}}") String title,
                         @Value("${portal.docs.description:}") String description) {
        var authenticationGuide = "로컬 개발용 API 문서입니다. 로그인 후 받은 accessToken을 Authorize의 bearerAuth에 입력하세요. "
            + "Bearer 접두어 없이 토큰 값만 입력합니다. Refresh는 로그인 시 저장된 HttpOnly 쿠키를 자동으로 사용합니다. "
            + "토큰 갱신 후에는 새 accessToken으로 Authorize를 다시 설정하세요. 페이지 번호는 0부터 시작합니다.";
        return new OpenAPI()
            .info(new Info().title(title).version("0.1.0")
                .description(description.isBlank() ? authenticationGuide : description))
            .servers(List.of(new Server().url("/").description("현재 실행 중인 서버")))
            .components(new Components()
                .addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                    .scheme("bearer").bearerFormat("JWT").description("로그인 응답의 accessToken. Bearer 접두어 없이 입력하세요."))
                .addSecuritySchemes("refreshCookie", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.COOKIE).name("portal_admin_refresh")
                    .description("로그인 응답에서 브라우저에 자동 저장되는 HttpOnly 쿠키입니다. 직접 입력하지 않아도 됩니다.")));
    }
}
