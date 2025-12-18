package ctfd;
// userA = owner of the flag
// userB = copied the flag
public record FlagCopied(String copycat, int id_userA, String userA, Integer id_userB, String userB, int id_challenge, String challenge, String flag) {}
