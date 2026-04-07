package talan.pfe.rulengine.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import talan.pfe.rulengine.dtos.rulesetexport.RuleSetExportPackage;
import talan.pfe.rulengine.entites.RuleSet;

public interface RuleSetSnapshotService {

    RuleSetExportPackage toPackage(RuleSet ruleSet);

    String toJson(RuleSet ruleSet) throws JsonProcessingException;

    RuleSetExportPackage parse(JsonNode node);

    RuleSetExportPackage parse(String json) throws JsonProcessingException;

    void applyPackage(RuleSet target, RuleSetExportPackage pkg);
}
