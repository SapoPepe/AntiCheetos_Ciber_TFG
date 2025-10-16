package ctfd;
// userA = owner of the flag
// userB = copied the flag
public record FlagCopied(int id_userA, String userA, int id_userB, String userB, int id_challenge, String challenge, String flag) {}
