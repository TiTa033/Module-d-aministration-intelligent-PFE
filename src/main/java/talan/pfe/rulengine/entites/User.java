package talan.pfe.rulengine.entites;
import jakarta.persistence.*;
import lombok.*;
import talan.pfe.rulengine.enums.Role;

@Entity @Table(name="users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private Role role;
    private Long tenantId;


//    @ManyToOne(fetch=FetchType.LAZY)
//    @JoinColumn(name="tenantId",insertable=false,updatable=false)
//    private Tenant tenant;

}
