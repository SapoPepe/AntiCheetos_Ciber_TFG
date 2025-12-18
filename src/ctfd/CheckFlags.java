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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Instant;

public class CheckFlags {
    private record Challenge(int id, String name){}
    private record Submission(int id_sub, int id_challenge, String name_challenge, int id_user, String name_user, Integer id_team, String name_team, String submitted_flag, boolean correct, String date){}

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
            // If challenge has dynamic flags
            if(getFlagType(url_base, api_key, c)){
                // All submissions --> divide correct and incorrect
                // For each correct, check with all corrects (do not check correct submissions from the same user)
                List<Submission> submissionList = getSubmissionsForChallenge(c.id(), api_key, url_base);
                checkCopies(copies, submissionList);
            }
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
        List<Submission> submissions = new ArrayList<>();
        int page = 1;

        try {
            while(true) {
                HttpResponse<String> response = apiRequest(url_base, "/submissions?challenge_id=" + id_challenge + "&page=" + page, api_key);
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray jsonSubmissions = jsonResponse.getJSONArray("data");

                for (int i = 0; i < jsonSubmissions.length(); i++) {
                    JSONObject o = jsonSubmissions.getJSONObject(i);
                    Integer id_team = o.isNull("team_id") ? null : o.getInt("team_id");
                    String name_team = o.isNull("team") ? null : o.getJSONObject("team").getString("name");
                    submissions.add(new Submission(o.getInt("id"), o.getInt("challenge_id"), o.getJSONObject("challenge").getString("name"), o.getInt("user_id"), o.getJSONObject("user").getString("name"), id_team, name_team, o.getString("provided"), o.getString("type").equals("correct"), o.getString("date")));
                }

                page++;

                // When the next page is null it indicates that there is not more submissions
                if(jsonResponse.getJSONObject("meta").getJSONObject("pagination").optString("next", null) == null) break;
            }
            return submissions;
        } catch (IOException e) {
            throw new Exception(e.getMessage());
        }
    }

    private void checkCopies(List<FlagCopied> copies, List<Submission> submissions){
        for(Submission s : submissions){
            if(!s.correct) continue;

            // Filter submissions
            String flag = s.submitted_flag();
            int id_user = s.id_user();
            List<Submission> matches = submissions.stream()
                    .filter(sub -> sub.correct)
                    .filter(sub -> sub.submitted_flag().equals(flag))
                    .filter(sub -> sub.id_user() != id_user)
                    .toList();


            // If matches is null and the submitted flags doesn't match with the real user flag, it indicates he has tried a random string and was accepted
            Instant submissionInstant = java.time.Instant.parse(s.date());
            Instant cutoff = java.time.Instant.parse("2025-09-01T00:00:00Z");

            if (matches.isEmpty() && !flag.contains(calculateDynamic(s.id_challenge(), id_user)) && !submissionInstant.isBefore(cutoff)) {
                copies.add(new FlagCopied(
                        null,
                        id_user,
                        s.name_user(),
                        null,
                        null,
                        s.id_challenge(),
                        s.name_challenge(),
                        flag
                ));
            }


            // Add to copies
            for(Submission sub : matches){
                // Add only if userA's ID is smaller to avoid duplicates like (A,B) and (B,A)
                if(id_user < sub.id_user) {
                    // Calculate who is the copicat
                    String dynamicA = calculateDynamic(s.id_challenge(), id_user);
                    String dynamicB = calculateDynamic(s.id_challenge(), sub.id_user());
                    String submitted_flag = sub.submitted_flag();

                    // If submitted_flag contains the dynamic, it means that userA is the owner of the flag. Otherwise, is userB
                    //String copicat = submitted_flag.contains(dynamic) ? sub.name_user() : s.name_user();

                    // Si la flag no es de ninguno de los dos usuarios, es una flag antigua calculada de otra forma y no cuenta
                    if(!submitted_flag.contains(dynamicA) && !submitted_flag.contains(dynamicB)) continue;

                    String copycat = submitted_flag.contains(dynamicA) ? sub.name_user() : s.name_user();

                    copies.add(new FlagCopied(
                            copycat,
                            id_user,                // ID userA
                            s.name_user(),          // Name userA
                            sub.id_user(),          // ID userB
                            sub.name_user(),        // Name userB
                            s.id_challenge(),       // ID challenge
                            s.name_challenge(),     // Name challenge
                            submitted_flag          // Copied flag
                    ));
                }
            }



        }


    }


    private boolean getFlagType(String url_base, String api_key, Challenge c) throws Exception {
        Pattern regexPattern = Pattern.compile("[\\Q<([{^=$!|]})?*+.>\\E\\\\]");
        String endpoint = "/challenges/" + c.id() + "/flags";
        try {
            HttpResponse<String> response = apiRequest(url_base, endpoint, api_key);
            JSONObject jsonResponse = new JSONObject(response.body());

            if(!jsonResponse.getJSONArray("data").getJSONObject(0).getString("type").equals("regex")) return false;

            String flag = jsonResponse.getJSONArray("data").getJSONObject(0).getString("content");

            // Delete first and last {} of the flag --- URJC{best_flags} -> URJCbest_flag
            flag = flag.replaceFirst("\\{", "");
            flag = flag.substring(0, flag.length()-1);

            Matcher searcher = regexPattern.matcher(flag);
            return searcher.find();


        } catch (IOException e) {
            throw new Exception(e.getMessage());
        }
    }


    private String calculateDynamic(int cId, int tId){
        String inputString = String.format("%d,%d", tId, cId);

        // Cold beer :-)
        int seed = 0;
        int h1 = 0xdeadbeef ^ seed;
        int h2 = 0x41c6ce57 ^ seed;

        for (int i = 0; i < inputString.length(); i++) {
            char ch = inputString.charAt(i);
            // Use the equivalent of 32 bits with sign for big numbers
            h1 = (h1 ^ ch) * -1640531535; // Equal to 2654435761
            h2 = (h2 ^ ch) * 1597334677;
        }

        h1 = (h1 ^ (h1 >>> 16)) * -2048144789;  // Equal to 2246822507
        h1 ^= (h2 ^ (h2 >>> 13)) * -1028477387; // Equal to 3266489909
        h2 = (h2 ^ (h2 >>> 16)) * -2048144789;  // Equal to 2246822507
        h2 ^= (h1 ^ (h1 >>> 13)) * -1028477387; // Equal to 3266489909

        long h1Unsigned = Integer.toUnsignedLong(h1);
        long hashResult = (4294967296L * (2097151 & h2)) + h1Unsigned;

        String hexResult = Long.toHexString(hashResult);

        String paddedHex = "0000000000" + hexResult;
        return paddedHex.substring(paddedHex.length() - 10);

    }

}
