package security.pwd

/**
  * Represents a hashed password.
  *
  * @param hash Password hash
  * @param salt Password salt
  */
case class Password(hash: String, salt: String)
