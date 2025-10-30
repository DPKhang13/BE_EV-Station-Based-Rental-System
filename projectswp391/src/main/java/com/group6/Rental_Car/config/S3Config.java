package com.group6.Rental_Car.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${longvan.s3.endpoint:https://s3-hcm5-r1.longvan.net}")
    private String endpoint;

    @Value("${longvan.s3.region:vn-hcm}") // placeholder, SDK cần có region
    private String region;

    @Value("${longvan.s3.accessKey}")
    private String accessKey;

    @Value("${longvan.s3.secretKey}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .checksumValidationEnabled(false)
                                .chunkedEncodingEnabled(false)
                                .build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .build();
    }
}
