package com.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "url_clicks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString
public class UrlClick {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "clicked_at", nullable = false)
    private Instant clickedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "ip_hash", nullable = false, length = 64)
    private String ipHash;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(length = 64)
    private String browser;

    @Column(name = "browser_version", length = 32)
    private String browserVersion;

    @Column(length = 64)
    private String os;

    @Column(name = "os_version", length = 32)
    private String osVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 32)
    private DeviceType deviceType;

    @Column(length = 2)
    private String country;

    @Column(columnDefinition = "text")
    private String referrer;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @PrePersist
    void onCreate() {
        if (clickedAt == null) {
            clickedAt = Instant.now();
        }
    }
}
