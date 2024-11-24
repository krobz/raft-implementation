package com.github.raftimpl.raft.example.server.machine;

import com.github.raftimpl.raft.StateMachine;
import com.github.raftimpl.raft.example.server.service.ExampleProto;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class MapDBStateMachine implements StateMachine {
    private static final Logger LOG = LoggerFactory.getLogger(MapDBStateMachine.class);
    private DB db;
    private HTreeMap<String, String> dataMap;
    private final String raftDataDir;

    public MapDBStateMachine(String raftDataDir) {
        this.raftDataDir = raftDataDir;
        initializeDB();
    }

    private void initializeDB() {
        String dbFilePath = raftDataDir + File.separator + "raft_mapdb";
        db = DBMaker.fileDB(new File(dbFilePath))
                .fileChannelEnable() // 使用 FileChannel，避免内存映射文件可能导致的问题
                .transactionEnable()
                .checksumHeaderBypass() // 跳过校验和，提升性能
                .make();
        dataMap = db.hashMap("data", Serializer.STRING, Serializer.STRING)
                .createOrOpen();
    }

    @Override
    public void writeSnapshot(String snapshotDir) {
        try {
            db.commit(); // 确保数据持久化
            String snapshotFilePath = snapshotDir + File.separator + "raft_snapshot";
            DB snapshotDB = DBMaker.fileDB(new File(snapshotFilePath))
                    .fileChannelEnable()
                    .checksumHeaderBypass()
                    .make();
            HTreeMap<String, String> snapshotMap = snapshotDB.hashMap("data", Serializer.STRING, Serializer.STRING)
                    .createOrOpen();
            snapshotMap.putAll(dataMap);
            snapshotDB.close();
        } catch (Exception e) {
            LOG.warn("writeSnapshot encountered exception: {}", e.getMessage(), e);
        }
    }

    @Override
    public void readSnapshot(String snapshotDir) {
        try {
            db.close();
            String snapshotFilePath = snapshotDir + File.separator + "raft_snapshot";
            File snapshotFile = new File(snapshotFilePath);
            if (snapshotFile.exists()) {
                db = DBMaker.fileDB(snapshotFile)
                        .fileChannelEnable()
                        .transactionEnable()
                        .checksumHeaderBypass()
                        .make();
                dataMap = db.hashMap("data", Serializer.STRING, Serializer.STRING)
                        .createOrOpen();
            } else {
                LOG.warn("Snapshot file does not exist at {}", snapshotFilePath);
                initializeDB(); // 如果快照不存在，重新初始化数据库
            }
        } catch (Exception e) {
            LOG.warn("readSnapshot encountered exception: {}", e.getMessage(), e);
        }
    }

    @Override
    public void apply(byte[] dataBytes) {
        try {
            ExampleProto.SetRequest request = ExampleProto.SetRequest.parseFrom(dataBytes);
            dataMap.put(request.getKey(), request.getValue());
            db.commit();
        } catch (Exception e) {
            LOG.warn("apply encountered exception: {}", e.getMessage(), e);
        }
    }

    @Override
    public byte[] get(byte[] dataBytes) {
        try {
            String key = new String(dataBytes, StandardCharsets.UTF_8);
            String value = dataMap.get(key);
            if (value != null) {
                return value.getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            LOG.warn("get encountered exception: {}", e.getMessage(), e);
        }
        return null;
    }
}
