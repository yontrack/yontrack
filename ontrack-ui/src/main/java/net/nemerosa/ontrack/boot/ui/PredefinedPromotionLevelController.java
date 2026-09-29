package net.nemerosa.ontrack.boot.ui;

import jakarta.validation.Valid;
import net.nemerosa.ontrack.common.Document;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.settings.PredefinedPromotionLevelService;
import net.nemerosa.ontrack.model.structure.ID;
import net.nemerosa.ontrack.model.structure.NameDescription;
import net.nemerosa.ontrack.model.structure.PredefinedPromotionLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;

/**
 * Access to the list of predefined promotion levels.
 *
 * @see PredefinedPromotionLevel
 */
@RestController
@RequestMapping("/rest/admin")
public class PredefinedPromotionLevelController {

    private final PredefinedPromotionLevelService predefinedPromotionLevelService;

    @Autowired
    public PredefinedPromotionLevelController(PredefinedPromotionLevelService predefinedPromotionLevelService) {
        this.predefinedPromotionLevelService = predefinedPromotionLevelService;
    }

    /**
     * Gets the list of predefined promotion levels.
     */
    @RequestMapping(value = "predefinedPromotionLevels", method = RequestMethod.GET)
    public List<PredefinedPromotionLevel> getPredefinedPromotionLevelList() {
        return predefinedPromotionLevelService.getPredefinedPromotionLevels();
    }

    @RequestMapping(value = "predefinedPromotionLevels/create", method = RequestMethod.POST)
    public PredefinedPromotionLevel newPredefinedPromotionLevel(@RequestBody @Valid NameDescription nameDescription) {
        return predefinedPromotionLevelService.newPredefinedPromotionLevel(
                PredefinedPromotionLevel.of(
                        nameDescription
                )
        );
    }

    @RequestMapping(value = "predefinedPromotionLevels/{predefinedPromotionLevelId}", method = RequestMethod.GET)
    public PredefinedPromotionLevel getPromotionLevel(@PathVariable ID predefinedPromotionLevelId) {
        return predefinedPromotionLevelService.getPredefinedPromotionLevel(predefinedPromotionLevelId);
    }

    @RequestMapping(value = "predefinedPromotionLevels/{predefinedPromotionLevelId}/update", method = RequestMethod.PUT)
    public PredefinedPromotionLevel updatePromotionLevel(@PathVariable ID predefinedPromotionLevelId, @RequestBody @Valid NameDescription nameDescription) {
        // Gets from the repository
        PredefinedPromotionLevel promotionLevel = predefinedPromotionLevelService.getPredefinedPromotionLevel(predefinedPromotionLevelId);
        // Updates
        promotionLevel = promotionLevel.update(nameDescription);
        // Saves in repository
        predefinedPromotionLevelService.savePredefinedPromotionLevel(promotionLevel);
        // OK
        return promotionLevel;
    }

    @RequestMapping(value = "predefinedPromotionLevels/{predefinedPromotionLevelId}", method = RequestMethod.DELETE)
    public Ack deletePromotionLevel(@PathVariable ID predefinedPromotionLevelId) {
        return predefinedPromotionLevelService.deletePredefinedPromotionLevel(predefinedPromotionLevelId);
    }

    @RequestMapping(value = "predefinedPromotionLevels/{predefinedPromotionLevelId}/image", method = RequestMethod.GET)
    public Document getPromotionLevelImage(@PathVariable ID predefinedPromotionLevelId) {
        return predefinedPromotionLevelService.getPredefinedPromotionLevelImage(predefinedPromotionLevelId);
    }

    @PutMapping("predefinedPromotionLevels/{predefinedPromotionLevelId}/image")
    @ResponseStatus(HttpStatus.OK)
    public void putPredefinedPromotionLevelImage(@PathVariable ID predefinedPromotionLevelId, @RequestBody String imageBase64) {
        predefinedPromotionLevelService.setPredefinedPromotionLevelImage(predefinedPromotionLevelId, new Document(
                "image/png",
                Base64.getDecoder().decode(imageBase64)
        ));
    }

}
