package ctfd;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class CheckFlags {
    private record Challenge(int id, String name){}
    private record Submission(int id_sub, int id_challenge, String name_challenge, int id_user, String name_user, Integer id_team, String name_team, String submitted_flag, boolean correct){}

    private static final HttpClient client = HttpClient.newHttpClient();


    public List<FlagCopied> checkFlags(String api_key, String url_base) throws Exception {
        // Get all challenges
        // For each challenge check if userA has introduced a flag from userB
        // Copy detected --> add to list of FlagCopied

        System.out.println("[*] Getting challenges");
        List<FlagCopied> copies = new ArrayList<>();
        List<Challenge> challenges = getAllChallenges(api_key, url_base);

        System.out.println("[*] Checking copies");
        for(Challenge c : challenges){
            // All submissions --> divide correct and incorrect
            // For each correct, check with all incorrect (do not check incorrect submissions from the same user)
            List<Submission> submissionList = getSubmissionsForChallenge(c.id(), api_key, url_base);
            checkCopies(copies, submissionList);
        }


        return copies;
    }


    private HttpResponse<String> apiRequest(String url_base, String endpoint, String api_key) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url_base + "/api/v1" + endpoint))
                .header("Authorization", "Token " + api_key)
                .header("Content-Type", "application/json")
                .GET()
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }


    private List<Challenge> getAllChallenges(String api_key, String url_base) throws Exception {
        try {
            HttpResponse<String> response = apiRequest(url_base,"/challenges", api_key);
            JSONObject jsonResponse = new JSONObject(response.body());
            JSONArray jsonChallenges = jsonResponse.getJSONArray("data");

            List<Challenge> challenges = new ArrayList<>();
            for(int i=0; i<jsonChallenges.length(); i++){
                JSONObject o = jsonChallenges.getJSONObject(i);
                challenges.add(new Challenge(o.getInt("id"), o.getString("name")));
            }
            return challenges;
        } catch (IOException | InterruptedException e) {
            throw new Exception(e.getMessage());
        }
    }


    private List<Submission> getSubmissionsForChallenge(int id_challenge, String api_key, String url_base) throws Exception {
        try {
            HttpResponse<String> response = apiRequest(url_base, "/submissions?challenge_id=" + id_challenge, api_key);
            JSONObject jsonResponse = new JSONObject(response.body());
            JSONArray jsonSubmissions = jsonResponse.getJSONArray("data");

            List<Submission> submissions = new ArrayList<>();
            for(int i=0; i< jsonResponse.length(); i++){
                JSONObject o = jsonSubmissions.getJSONObject(i);
                Integer id_team = o.isNull("team_id") ? null : o.getInt("team_id");
                String name_team = o.isNull("team") ? null : o.getJSONObject("team").getString("name");
                submissions.add(new Submission(o.getInt("id"), o.getInt("challenge_id"), o.getJSONObject("challenge").getString("name"), o.getInt("user_id"), o.getJSONObject("user").getString("name"), id_team, name_team, o.getString("provided"), o.getString("type").equals("correct")));
            }
            return submissions;
        } catch (IOException e) {
            throw new Exception(e.getMessage());
        }
    }

    private void checkCopies(List<FlagCopied> copies, List<Submission> submissions){
        for(Submission s : submissions){
            // Filter submissions
            String flag = s.submitted_flag();
            int id_user = s.id_user();
            List<Submission> matches = submissions.stream()
                    .filter(sub -> !sub.correct)
                    .filter(sub -> sub.submitted_flag().equals(flag))
                    .filter(sub -> sub.id_user() != id_user)
                    .toList();

            // Add to copies
            for(Submission sub : matches){
                copies.add(new FlagCopied(
                        id_user,                // ID userA
                        s.name_user(),          // Name userA
                        sub.id_user(),          // ID userB
                        sub.name_user(),        // Name userB
                        s.id_challenge(),       // ID challenge
                        s.name_challenge(),     // Name challenge
                        sub.submitted_flag()    // Copied flag
                ));
            }

        }
    }
}
