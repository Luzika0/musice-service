package user.music_service.config;

import java.util.Optional;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import lombok.RequiredArgsConstructor;
import user.music_service.dto.request.RequestHeaderData;

@RequiredArgsConstructor
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig implements AuditorAware<Integer> {

    private final RequestHeaderData requestHeaderData;

    @Override
    public Optional<Integer> getCurrentAuditor() {

        Integer userId = requestHeaderData.getUserId();
        return Optional.ofNullable(userId);
    }
}
