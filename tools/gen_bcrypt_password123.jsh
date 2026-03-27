import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
var enc = new BCryptPasswordEncoder();
System.out.println(enc.encode("password123"));
