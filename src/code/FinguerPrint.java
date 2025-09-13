package code;

//----------------------------------//
// Represents a single fingerprint  //
//----------------------------------//

// record = special class in java that is immutable and has a constructor, getters, equals, hashCode and toString methods
// position = position of the normalized document
public record FinguerPrint(String hash, int position, String documentID) {}
