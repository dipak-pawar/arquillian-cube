package org.arquillian.cube.docker.impl.client.reporter;

import io.fabric8.docker.api.model.BlkioStatEntry;
import io.fabric8.docker.api.model.MemoryStats;
import io.fabric8.docker.api.model.Stats;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.arquillian.cube.docker.impl.client.utils.NumberConversion;

public class ContainerStatsBuilder {

    public static CubeStatistics updateStats(Stats statistics) {

        CubeStatistics stats = new CubeStatistics();

        final List<BlkioStatEntry> ioServiceBytesRecursive = statistics.getBlkioStats().getIoServiceBytesRecursive();

      //  Map<String, Long> blkio = extractIORW(statistics.getBlkioStats().getIoServiceBytesRecursive());
        final MemoryStats memoryStats = statistics.getMemoryStats();

        /*stats.setIoBytesRead(blkio.get("io_bytes_read"));
        stats.setIoBytesWrite(blkio.get("io_bytes_write"));
        stats.setMaxUsage(memoryStats.getMaxUsage());
        stats.setUsage(memoryStats.getUsage());
        stats.setLimit(memoryStats.getLimit());

        stats.setNetworks(extractNetworksStats(statistics.getNetworks()));*/


        return stats;
    }

    private static Map<String, Map<String, Long>> extractNetworksStats(Map<String, Object> map) {
        Map<String, Map<String, Long>> nwStatsForEachNICAndTotal = new LinkedHashMap<>();
        if (map != null) {
            long totalRxBytes = 0, totalTxBytes = 0;

            for (Map.Entry<String, Object> entry: map.entrySet()) {
                Map<String, Long> nwStats = new LinkedHashMap<>();
                String adapterName = entry.getKey();
                if (entry.getValue() instanceof LinkedHashMap) {

                    Map<String, ?> adapter = (LinkedHashMap) entry.getValue();

                    long rxBytes = NumberConversion.convertToLong(adapter.get("rx_bytes"));
                    long txBytes = NumberConversion.convertToLong(adapter.get("tx_bytes"));

                    nwStats.put("rx_bytes", rxBytes);
                    nwStats.put("tx_bytes", txBytes);
                    nwStatsForEachNICAndTotal.put(adapterName, nwStats);

                    totalRxBytes += rxBytes;
                    totalTxBytes += txBytes;
                }
            }

            Map<String, Long> total = new LinkedHashMap<>();

            total.put("rx_bytes", totalRxBytes);
            total.put("tx_bytes", totalTxBytes);
            nwStatsForEachNICAndTotal.put("Total", total);
        }
        return nwStatsForEachNICAndTotal;
    }

    private static Map<String, Long> extractIORW(Map<String, Object> blkioStats) {
        Map<String, Long> blkrwStats = new LinkedHashMap<>();
        if (blkioStats != null && !blkioStats.isEmpty()) {
            List<LinkedHashMap> bios = (ArrayList<LinkedHashMap>) blkioStats.get("io_service_bytes_recursive");
            long read = 0, write = 0;
            if (bios != null) {
                for (Map<String, ?> io : bios) {
                    if (io != null) {
                        switch ((String) io.get("op")) {
                            case "Read":
                                read = NumberConversion.convertToLong(io.get("value"));
                                break;
                            case "Write":
                                write = NumberConversion.convertToLong(io.get("value"));
                                break;
                        }
                    }
                }
            }
            blkrwStats.put("io_bytes_read", read);
            blkrwStats.put("io_bytes_write", write);
        }
        return blkrwStats;
    }

    private static Map<String, Long> extractMemoryStats(Map<String, Object> map, String... fields) {
        Map<String, Long> memory = new LinkedHashMap<>();
        if (map != null) {
            for (String field: fields) {
                long usage = NumberConversion.convertToLong(map.get(field));
                memory.put(field, usage);
            }
        }
        return memory;
    }
}
