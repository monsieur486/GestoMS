package com.mr486.generator.dto;

import lombok.Data;

@Data
public class FeatureOptions {
    private boolean keycloak = true;
    private boolean redis = true;
    private boolean rabbitmq = true;
    private boolean websocket = true;
    private boolean admin = true;
    private boolean grafana = false;
    private boolean loki = false;
}
