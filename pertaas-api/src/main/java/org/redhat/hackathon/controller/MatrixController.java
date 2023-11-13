package org.redhat.hackathon.controller;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.json.JsonArray;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.query.QueryOptions;
import com.couchbase.client.java.query.QueryScanConsistency;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Path("/metrics")
public class MatrixController {

    @Inject
    Cluster cluster;

    @Inject
    Bucket bucket;

    @GET
    @Path("/{jobId}")
    @RunOnVirtualThread
    public String getMetrics(String jobId) {
        String query = """
                    SELECT SPLIT(p.key_tx,"::")[1] AS scan_timestamp,
                           TO_STRING(p.`vertx.http.client.active.connections`[0].`value`) active_connections,
                           ARRAY {"request_sent Method:" || v.`tags`.`method` || " Path:" || v.`tags`.`path`: TO_STRING(v.`count`)} FOR v IN p.`vertx.http.client.requests` END request_sent,
                           ARRAY {"request_bytes Method:" || v.`tags`.`method` || " Path:" || v.`tags`.`path`: TO_STRING(v.`total`)} FOR v IN p.`vertx.http.client.request.bytes` END request_bytes,
                           ARRAY {"response_bytes Code:" || v.`tags`.`code` || " Method:" || v.`tags`.`method` || " Path:" || v.`tags`.`path`: TO_STRING(v.`total`)} FOR v IN p.`vertx.http.client.response.bytes` END response_bytes,
                           ARRAY {"response_time_percentile Code:" || v.`tags`.`code` || " Method:" || v.`tags`.`method` || " Path:" || v.`tags`.`path` || " %tile:" || v.`tags`.`phi`: TO_STRING(v.`value`)} FOR v IN (
                               SELECT RAW h
                               FROM p.`vertx.http.client.response.time.percentile` h
                               ORDER BY h.`tags`.`method`,
                                        h.`tags`.`path`,
                                        LOWER(h.`tags`.`phi`) ASC ) END response_time_percentile,
                           ARRAY {"response_received Code:" || v.`tags`.`code` || " Method:" || v.`tags`.`method` || " Path:" || v.`tags`.`path`: TO_STRING(v.`count`)} FOR v IN p.`vertx.http.client.responses` END response_received
                    FROM `~BUCKET_NAME~` p
                    WHERE p.key_tx LIKE "~JOB_ID~::%"
                        AND p.registry = "StepMeterRegistry"
                    ORDER BY SPLIT(p.key_tx,"::")[1] ASC
                """.replace("~BUCKET_NAME~", bucket.name()).replace("~JOB_ID~", jobId);
        Log.info(jobId + " | Query: " + query);
        List<JsonObject> queryResult = cluster.query(query, QueryOptions.queryOptions().readonly(true).scanConsistency(QueryScanConsistency.REQUEST_PLUS))
                .rowsAsObject();
        Log.info(jobId + " | Result size: " + queryResult.size());
        if (queryResult.size() > 0) {
            // Create the column header for the table
            Set<String> columnHeaders = new LinkedHashSet<>();
            columnHeaders.add("scan_timestamp");
            columnHeaders.add("active_connections");

            for (JsonObject jsonObject : queryResult) {
                if (jsonObject.getArray("request_sent") != null)
                    jsonObject.getArray("request_sent").forEach(o -> columnHeaders.addAll(((JsonObject) o).toMap().keySet()));
            }
            for (JsonObject jsonObject : queryResult) {
                if (jsonObject.getArray("request_bytes") != null)
                    jsonObject.getArray("request_bytes").forEach(o -> columnHeaders.addAll(((JsonObject) o).toMap().keySet()));
            }
            for (JsonObject jsonObject : queryResult) {
                if (jsonObject.getArray("response_received") != null)
                    jsonObject.getArray("response_received").forEach(o -> columnHeaders.addAll(((JsonObject) o).toMap().keySet()));
            }
            for (JsonObject jsonObject : queryResult) {
                if (jsonObject.getArray("response_bytes") != null)
                    jsonObject.getArray("response_bytes").forEach(o -> columnHeaders.addAll(((JsonObject) o).toMap().keySet()));
            }
            // Get the column headers for response type percentiles as there might be many
            for (JsonObject jsonObject : queryResult) {
                if (jsonObject.getArray("response_time_percentile") != null)
                    jsonObject.getArray("response_time_percentile").forEach(o -> columnHeaders.addAll(((JsonObject) o).toMap().keySet()));
            }
            List<List<String>> combinedRows = new ArrayList<>();
            // Loop through the set to fetch the values
            for (JsonObject jsonObject : queryResult) {
                List<String> rowValues = new ArrayList<>();
                for (String columnName : columnHeaders) {
                    switch (columnName) {
                        case "scan_timestamp", "active_connections" ->
                                rowValues.add(jsonObject.getString(columnName) == null ? "" : jsonObject.getString(columnName));
                        case String s when s.startsWith("request_sent ") -> {
                            AtomicReference<String> stringAtomicReference = new AtomicReference<>("");
                            if (jsonObject.getArray("request_sent") != null)
                                jsonObject.getArray("request_sent").forEach(o -> {
                                    JsonObject j = (JsonObject) o;
                                    if (j.getString(s) != null) {
                                        stringAtomicReference.set(j.getString(columnName));
                                    }
                                });
                            rowValues.add(stringAtomicReference.get());
                        }
                        case String s when s.startsWith("request_bytes ") -> {
                            AtomicReference<String> stringAtomicReference = new AtomicReference<>("");
                            if (jsonObject.getArray("request_bytes") != null)
                                jsonObject.getArray("request_bytes").forEach(o -> {
                                    JsonObject j = (JsonObject) o;
                                    if (j.getString(s) != null) {
                                        stringAtomicReference.set(j.getString(s));
                                    }
                                });
                            rowValues.add(stringAtomicReference.get());
                        }
                        case String s when s.startsWith("response_received ") -> {
                            AtomicReference<String> stringAtomicReference = new AtomicReference<>("");
                            if (jsonObject.getArray("response_received") != null)
                                jsonObject.getArray("response_received").forEach(o -> {
                                    JsonObject j = (JsonObject) o;
                                    if (j.getString(s) != null) {
                                        stringAtomicReference.set(j.getString(columnName));
                                    }
                                });
                            rowValues.add(stringAtomicReference.get());
                        }
                        case String s when s.startsWith("response_bytes ") -> {
                            AtomicReference<String> stringAtomicReference = new AtomicReference<>("");
                            if (jsonObject.getArray("response_bytes") != null)
                                jsonObject.getArray("response_bytes").forEach(o -> {
                                    JsonObject j = (JsonObject) o;
                                    if (j.getString(s) != null) {
                                        stringAtomicReference.set(j.getString(columnName));
                                    }
                                });
                            rowValues.add(stringAtomicReference.get());
                        }
                        case String s when s.startsWith("response_time_percentile ") -> {
                            AtomicReference<String> stringAtomicReference = new AtomicReference<>("");
                            if (jsonObject.getArray("response_time_percentile") != null)
                                jsonObject.getArray("response_time_percentile").forEach(o -> {
                                    JsonObject j = (JsonObject) o;
                                    if (j.getString(s) != null) {
                                        stringAtomicReference.set(j.getString(columnName));
                                    }
                                });
                            rowValues.add(stringAtomicReference.get());
                        }
                        default -> throw new IllegalStateException("Unexpected value: " + columnName);
                    }
                }
                combinedRows.add(rowValues);
            }
            Set<String> columnNamesToDisplay = getFormattedColumnNames(columnHeaders);
            // Data in datatable format
            // Column names
            Set<JsonObject> columns = new LinkedHashSet<>();
            for (String columnName : columnNamesToDisplay) {
                columns.add(JsonObject.create().put("title", columnName));
            }
            // Data
            JsonArray data = JsonArray.from(combinedRows.toArray());
            // Return table
            return JsonObject.create().put("columns", JsonArray.from(columns.toArray())).put("data", data).toString();
        } else {
            return JsonObject.create().put("columns", JsonArray.create().add(JsonObject.create().put("title", "-"))).put("data", JsonArray.create().add(JsonArray.create().add("No Data to display"))).toString();
        }
    }

    private Set<String> getFormattedColumnNames(Set<String> columnHeaders) {
        Set<String> columnNamesToDisplay = new LinkedHashSet<>();
        for (String columnHeader : columnHeaders) {
            columnNamesToDisplay.add(switch (columnHeader) {
                case "scan_timestamp":
                    yield "Scan Timestamp";
                case "active_connections":
                    yield "Active conn";
                case String s when s.startsWith("request_sent "):
                    yield s.replace("request_sent", "Request Sent");
                case String s when s.startsWith("request_bytes "):
                    yield s.replace("request_bytes", "Request Bytes");
                case String s when s.startsWith("response_received "):
                    yield s.replace("response_received", "Response Received");
                case String s when s.startsWith("response_bytes "):
                    yield s.replace("response_bytes", "Response Bytes");
                case String s when s.startsWith("response_time_percentile "):
                    yield s.replace("response_time_percentile", "Response Time");
                default:
                    throw new IllegalStateException("Unexpected value: " + columnHeader);
            });
        }
        return columnNamesToDisplay;
    }
}

