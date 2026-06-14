package domjudgeDownloader;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.zip.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DomjudgeDownloader {
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String baseApi;
    private final String cookieHeader;


    public DomjudgeDownloader(String baseApi, String oauth2Proxy, String phpSessId){
        this.cookieHeader = "_oauth2_proxy=" + oauth2Proxy + "; PHPSESSID=" + phpSessId;
        this.baseApi = baseApi.endsWith("/") ? baseApi.substring(0, baseApi.length()-1) : baseApi;
    }

    public Path downloader(String cid) throws Exception{
        System.out.println("\r[*] Searching submissions for " + cid + "...");

        // 1. GET judgements for the contest
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(this.baseApi + "/api/v4/contests/" + cid + "/judgements?strict=false"))
                .header("accept", "application/json")
                .header("Cookie", this.cookieHeader)
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // Filter by judgement_type_id == AC
        JsonNode root = mapper.readTree(response.body());
        HashMap<ProblemTeamKey, SubmissionInfo> sub_time_map = new HashMap<>();
        int total = root.size();
        int count = 0;

        if (root.isArray()) {
            for (JsonNode node : root) {
                count++;
                if (count % 10 == 0 || count == total) {
                    int percent = (int) ((count * 100.0) / total);
                    System.out.print("\r[*] Filtering submissions (it might take some minutes)... " + percent + "%");
                }

                if ("AC".equals(node.path("judgement_type_id").asText())) {
                    // 2. GET the given submission for the contest
                    request = HttpRequest.newBuilder()
                            .uri(new URI(this.baseApi + "/api/v4/contests/" + cid + "/submissions/" + node.path("submission_id").asText() + "?strict=false"))
                            .header("accept", "application/json")
                            .header("Cookie", this.cookieHeader)
                            .GET()
                            .build();
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    JsonNode root2 = mapper.readTree(response.body());
                    String team_id = root2.path("team_id").asText();
                    String problem_id = root2.path("problem_id").asText();
                    String submission_id = node.path("submission_id").asText();
                    ZonedDateTime sub_time = ZonedDateTime.parse(root2.path("time").asText());

                    ProblemTeamKey key = new ProblemTeamKey(problem_id, team_id);
                    if(!sub_time_map.containsKey(key) || sub_time.isBefore(sub_time_map.get(key).getTime())){
                        sub_time_map.put(key, new SubmissionInfo(sub_time, submission_id));
                    }
                }
            }
            System.out.println("\r[*] Filtering submissions (it might take some minutes)... 100%");
        }

        System.out.println("[*] Downloading files in ./domjudgeDownloads ... ");

        // 3. Download the files
        for(Map.Entry<ProblemTeamKey, SubmissionInfo> entry : sub_time_map.entrySet()){
            String submission_id = entry.getValue().getSubmissionId();
            String problem_id = entry.getKey().getProblemId();
            String team_id = entry.getKey().getTeamId();

            request = HttpRequest.newBuilder()
                    .uri(new URI(this.baseApi + "/api/v4/submissions/" + submission_id + "/files?strict=false"))
                    .header("accept", "application/zip")
                    .header("Cookie", this.cookieHeader)
                    .GET()
                    .build();

            HttpResponse<InputStream> zipResponse = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            Path outputDir = Paths.get("domjudgeDownloads", problem_id);
            if(!Files.exists(outputDir)) Files.createDirectories(outputDir);
            ZipInputStream zis = new ZipInputStream(zipResponse.body());
            ZipEntry zipEntry = zis.getNextEntry();
            if (zipEntry != null) {
                String originalFileName = zipEntry.getName();
                Path outFile = outputDir.resolve(team_id + "_" + originalFileName);
                Files.copy(zis, outFile, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }

        return Paths.get("domjudgeDownloads");
    }
}