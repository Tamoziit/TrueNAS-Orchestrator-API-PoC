package com.tamojit.nasorchestrator.service;

import com.tamojit.nasorchestrator.client.TrueNasStorageClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "smb.bootstrap.enabled", havingValue = "true")
public class StorageBoostrapService {
    private static final Logger log = LoggerFactory.getLogger(StorageBoostrapService.class);
    private final TrueNasStorageClient trueNasStorageClient;

    @Value("${smb.bootstrap.pool-name}")
    private String poolName;

    @Value("${smb.bootstrap.disk-identifier}")
    private String diskIdentifier;

    @Value("${smb.bootstrap.dataset-name}")
    private String datasetName;

    @Value("${smb.bootstrap.share-name}")
    private String shareName;

    @Value("${smb.username}")
    private String smbUsername;

    @Value("${smb.full-name}")
    private String smbFullName;

    @Value("${smb.password}")
    private String smbPassword;

    public StorageBoostrapService(TrueNasStorageClient trueNasStorageClient) {
        this.trueNasStorageClient = trueNasStorageClient;
    }

    @PostConstruct
    public void provisionBaselineStorage() {
        if (!trueNasStorageClient.userExists(smbUsername)) {
            log.info("Creating Baseline storage for user {}", smbUsername);
            trueNasStorageClient.createUser(smbUsername, smbFullName, smbPassword);
        }

        if (!trueNasStorageClient.poolExists(poolName)) {
            log.info("Creating pool {}", poolName);
            trueNasStorageClient.createPool(poolName, diskIdentifier);
        }

        if (!trueNasStorageClient.datasetExists(poolName, datasetName)) {
            log.info("Creating dataset {}", datasetName);
            trueNasStorageClient.createDataset(poolName, datasetName);
        }

        String datasetPath = "/mnt/" + poolName + "/" + datasetName;
        trueNasStorageClient.setDatasetPermissions(datasetPath, null, null, "777");

        if (!trueNasStorageClient.smbShareExists(shareName)) {
            log.info("Creating share {}", shareName);
            trueNasStorageClient.createSmbShare(datasetPath, shareName);
        }
        trueNasStorageClient.startService("cifs");

        log.info("Baseline SMB storage provisioned: user={}, pool={}, dataset={}, share={}", smbUsername, poolName, datasetName, shareName);
    }
}
