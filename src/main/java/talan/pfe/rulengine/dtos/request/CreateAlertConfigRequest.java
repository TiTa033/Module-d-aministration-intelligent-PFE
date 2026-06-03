package talan.pfe.rulengine.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import talan.pfe.rulengine.enums.AlertCondition;
import talan.pfe.rulengine.enums.AlertMetric;

@Getter
@Setter
public class CreateAlertConfigRequest {

    @NotBlank
    private String name;

    @NotNull
    private AlertMetric metric;

    @NotNull
    private AlertCondition conditionType;

    @NotNull
    @Positive
    private Double threshold;

    @NotNull
    @Positive
    private Integer windowHours;

    /** Optionnel — si null, l'alerte porte sur tous les RuleSets du tenant */
    private Long ruleSetId;
}
