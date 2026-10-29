package com.smartdoor.backend.infrastructure.mqtt;

import com.smartdoor.backend.configuration.AppProperties;
import com.smartdoor.backend.credentials.service.CredentialSnapshotService;
import com.smartdoor.backend.device.entity.Device;
import com.smartdoor.backend.device.repository.DeviceRepository;
import com.smartdoor.backend.door.repository.DoorRepository;
import com.smartdoor.backend.infrastructure.outbox.OutboxDelivery;
import com.smartdoor.backend.infrastructure.outbox.OutboxTransport;
import com.smartdoor.backend.settings.repository.DoorConfigRepository;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** MQTT transport for configuration, credential snapshots and authenticated device events only. */
@Component
@ConditionalOnProperty(name="app.settings-mqtt.enabled",havingValue="true")
public class SettingsMqttBridge implements OutboxTransport {
    private static final Logger log=LoggerFactory.getLogger(SettingsMqttBridge.class);
    private final AppProperties.Mqtt settings;
    private final SettingsMqttProperties properties;
    private final SignedDeviceMessageService incoming;
    private final DoorRepository doors;
    private final DeviceRepository devices;
    private final DoorConfigRepository configs;
    private final CredentialSnapshotService credentials;
    private final ObjectMapper mapper;
    private final MqttAsyncClient client;
    private final AtomicLong connectionGeneration=new AtomicLong();
    private final ThreadPoolExecutor receiver=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r -> {
        Thread thread=new Thread(r,"settings-mqtt-events"); thread.setDaemon(true); return thread;
    });
    private volatile boolean closing;

    public SettingsMqttBridge(AppProperties app,SettingsMqttProperties properties,SignedDeviceMessageService incoming,
            DoorRepository doors,DeviceRepository devices,DoorConfigRepository configs,
            CredentialSnapshotService credentials,ObjectMapper mapper,Environment environment) throws java.io.IOException,MqttException {
        this.settings=app.mqtt(); this.properties=properties; this.incoming=incoming; this.doors=doors;
        this.devices=devices; this.configs=configs; this.credentials=credentials; this.mapper=mapper;
        if (environment.matchesProfiles("prod") && !properties.tls())
            throw new IllegalStateException("MQTT TLS is required in production.");
        // Validate the master key before advertising a working device integration.
        try {
            if (properties.deviceMasterKey()==null || java.util.Base64.getDecoder().decode(properties.deviceMasterKey()).length<32)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw new IllegalStateException("DEVICE_MASTER_KEY must be Base64 of at least 32 random bytes."); }
        Path persistence=Path.of(environment.getProperty("app.settings-mqtt.persistence-dir",".local/mqtt/backend"));
        Files.createDirectories(persistence);
        client=new MqttAsyncClient((properties.tls()?"ssl://":"tcp://")+settings.host()+":"+settings.port(),
                environment.getProperty("app.settings-mqtt.client-id","backend-settings"),
                new MqttDefaultFilePersistence(persistence.toString()));
        client.setManualAcks(true);
        client.setCallback(new MqttCallbackExtended() {
            @Override public void connectComplete(boolean reconnect,String serverURI) {
                connectionGeneration.incrementAndGet();
                try {
                    client.subscribe(new String[]{eventTopic("sync-ack"),eventTopic("alert"),eventTopic("heartbeat")},new int[]{1,1,1});
                    log.info("Settings MQTT connected and subscribed.");
                } catch (MqttException e) { log.warn("Settings MQTT subscription failed (reason {}).",e.getReasonCode()); }
            }
            @Override public void connectionLost(Throwable cause) { connectionGeneration.incrementAndGet(); log.warn("Settings MQTT disconnected; outbox will retry."); }
            @Override public void messageArrived(String topic,MqttMessage message) {
                long generation=connectionGeneration.get();
                // Keep database locks off Paho's callback thread, which also completes outgoing PUBACKs.
                receiver.execute(() -> receive(topic,message,generation));
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) { }
        });
    }

    private static String eventTopic(String type) { return "smart-door/v1/doors/+/devices/+/events/"+type; }

    private void receive(String topic,MqttMessage message,long generation) {
        try {
            try { incoming.receive(topic,message.getPayload()); }
            catch (com.smartdoor.backend.shared.error.ApplicationException invalid) {
                log.warn("Rejected unauthenticated or invalid device event on {}.",topic);
            }
            // Acknowledge after the transaction commits (or after rejecting an invalid message).
            if (client.isConnected() && generation==connectionGeneration.get())
                client.messageArrivedComplete(message.getId(),message.getQos());
        } catch (Exception transientError) {
            log.warn("Device event processing deferred ({}); no MQTT acknowledgement sent.",transientError.getClass().getSimpleName());
            if (generation==connectionGeneration.get()) {
                try { client.disconnectForcibly(1000,1000,false); }
                catch (MqttException ignored) { }
            }
        }
    }

    @Scheduled(initialDelay=1000,fixedDelay=10000)
    public synchronized void connect() {
        if (closing || client.isConnected()) return;
        try {
            MqttConnectOptions options=new MqttConnectOptions();
            options.setUserName(settings.username()); options.setPassword(settings.password().toCharArray());
            options.setAutomaticReconnect(true); options.setCleanSession(false);
            options.setConnectionTimeout((int)Math.max(1,settings.connectionTimeout().toSeconds()));
            options.setKeepAliveInterval(30);
            client.connect(options).waitForCompletion(settings.connectionTimeout().toMillis()+1000);
        } catch (MqttException e) { log.warn("Settings MQTT connection pending (reason {}).",e.getReasonCode()); }
    }

    @Override public boolean supports(String kind) { return "CONFIGURATION".equals(kind) || "CREDENTIALS".equals(kind); }

    @Override @Transactional
    public void deliver(OutboxDelivery message) throws Exception {
        if (!client.isConnected()) throw new IllegalStateException("MQTT disconnected");
        var door=doors.findByIdForUpdate(message.doorId()).orElseThrow();
        Device device=devices.findByDeviceCode(message.deviceCode()).orElseThrow();
        if (!device.getDoorId().equals(door.getId()) || door.getOwnerAccountId()==null
                || device.getLifecycleStatus()==Device.LifecycleStatus.RETIRED)
            throw new IllegalStateException("Device is no longer eligible for delivery");
        var config=configs.findById(door.getId()).orElseThrow();
        var payload=new LinkedHashMap<String,Object>();
        payload.put("schemaVersion",1);
        String command;
        if ("CONFIGURATION".equals(message.kind())) {
            command="configuration";
            payload.put("version",config.getVersion());
            payload.put("maxFailedAttempts",config.getMaxFailedAttempts());
            payload.put("lockoutSeconds",config.getLockoutSeconds());
            payload.put("temperatureThreshold",config.getTemperatureThreshold());
            payload.put("openTimeoutSeconds",config.getOpenTimeoutSeconds());
        } else if ("CREDENTIALS".equals(message.kind()) && device.getNodeType()==Device.NodeType.NODE_1) {
            command="credentials";
            var snapshot=credentials.snapshot(door.getId());
            payload.put("version",snapshot.version()); payload.put("reason","FULL_REFRESH");
            payload.put("pinVerifier",snapshot.pinVerifier()); payload.put("cards",snapshot.cards());
        } else throw new IllegalArgumentException("Unsupported delivery kind for device");
        byte[] bytes=mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
        if (bytes.length>properties.maxPayloadBytes()) throw new IllegalStateException("Device snapshot exceeds payload limit");
        // Serialize with settings writers and rebuild the latest desired state. An old retry never rolls a retained version back.
        client.publish("smart-door/v1/doors/"+door.getId()+"/devices/"+device.getDeviceCode()+"/commands/"+command,
                bytes,1,true).waitForCompletion(settings.connectionTimeout().toMillis()+1000);
    }

    @PreDestroy public synchronized void close() {
        closing=true;
        receiver.shutdown();
        try { if (!receiver.awaitTermination(3,TimeUnit.SECONDS)) receiver.shutdownNow(); }
        catch (InterruptedException e) { receiver.shutdownNow(); Thread.currentThread().interrupt(); }
        try { if (client.isConnected()) client.disconnect().waitForCompletion(3000); client.close(true); }
        catch (MqttException e) { log.debug("MQTT client closed with reason {}.",e.getReasonCode()); }
    }
}
