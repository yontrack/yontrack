package net.nemerosa.ontrack.boot.ui;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import net.nemerosa.ontrack.common.Document;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.deprecation.DeprecationService;
import net.nemerosa.ontrack.model.deprecation.DeprecationSurface;
import net.nemerosa.ontrack.model.structure.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static net.nemerosa.ontrack.ui.support.UIUtils.setupDefaultImageCache;

@RestController
@RequestMapping("/rest/structure")
public class PromotionLevelController {

    private final StructureService structureService;
    private final DeprecationService deprecationService;

    @Autowired
    public PromotionLevelController(StructureService structureService, DeprecationService deprecationService) {
        this.structureService = structureService;
        this.deprecationService = deprecationService;
    }

    // Promotion levels

    @RequestMapping(value = "branches/{branchId}/promotionLevels", method = RequestMethod.GET)
    public List<PromotionLevel> getPromotionLevelListForBranch(@PathVariable ID branchId) {
        return structureService.getPromotionLevelListForBranch(branchId);
    }

    @RequestMapping(value = "branches/{branchId}/promotionLevels/reorder", method = RequestMethod.PUT)
    public List<PromotionLevel> reorderPromotionLevelListForBranch(@PathVariable ID branchId, @RequestBody Reordering reordering) {
        // Reordering
        structureService.reorderPromotionLevels(branchId, reordering);
        // OK
        return getPromotionLevelListForBranch(branchId);
    }

    @RequestMapping(value = "branches/{branchId}/promotionLevels/create", method = RequestMethod.POST)
    public ResponseEntity<PromotionLevel> newPromotionLevel(@PathVariable ID branchId, @RequestBody @Valid NameDescription nameDescription) {
        // Gets the holding branch
        Branch branch = structureService.getBranch(branchId);
        // Creates a new promotion level
        PromotionLevel promotionLevel = PromotionLevel.of(branch, nameDescription);
        // Saves it into the repository
        promotionLevel = structureService.newPromotionLevel(promotionLevel);
        // OK
        return ResponseEntity.ok(promotionLevel);
    }

    @RequestMapping(value = "promotionLevels/{promotionLevelId}", method = RequestMethod.GET)
    public ResponseEntity<PromotionLevel> getPromotionLevel(@PathVariable ID promotionLevelId) {
        return ResponseEntity.ok(structureService.getPromotionLevel(promotionLevelId));
    }

    @RequestMapping(value = "promotionLevels/{promotionLevelId}/update", method = RequestMethod.PUT)
    public ResponseEntity<PromotionLevel> updatePromotionLevel(@PathVariable ID promotionLevelId, @RequestBody @Valid NameDescription nameDescription) {
        // Gets from the repository
        PromotionLevel promotionLevel = structureService.getPromotionLevel(promotionLevelId);
        // Updates
        promotionLevel = promotionLevel.update(nameDescription);
        // Saves in repository
        structureService.savePromotionLevel(promotionLevel);
        // As resource
        return ResponseEntity.ok(promotionLevel);
    }

    @RequestMapping(value = "promotionLevels/{promotionLevelId}", method = RequestMethod.DELETE)
    public ResponseEntity<Ack> deletePromotionLevel(@PathVariable ID promotionLevelId) {
        return ResponseEntity.ok(structureService.deletePromotionLevel(promotionLevelId));
    }

    @GetMapping("promotionLevels/{promotionLevelId}/image")
    public Document getPromotionLevelImage_(HttpServletResponse response, @PathVariable int promotionLevelId) {
        Document document = structureService.getPromotionLevelImage(ID.of(promotionLevelId));
        setupDefaultImageCache(response, document);
        return document;
    }

    @PutMapping("promotionLevels/{promotionLevelId}/image")
    @ResponseStatus(HttpStatus.OK)
    public void putPromotionLevelImage(@PathVariable ID promotionLevelId, @RequestBody String imageBase64) {
        structureService.setPromotionLevelImage(
                promotionLevelId,
                new Document(
                        "image/png",
                        Base64.getDecoder().decode(imageBase64)
                )
        );
    }

    /**
     * @deprecated Removed in V6. Use the PUT method instead. See #1922
     */
    @RequestMapping(value = "promotionLevels/{promotionLevelId}/image", method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Deprecated(forRemoval = true)
    public void setPromotionLevelImage(@PathVariable ID promotionLevelId, @RequestParam MultipartFile file) throws IOException {
        deprecationService.deprecatedUsage(
                DeprecationSurface.REST,
                "POST /rest/structure/promotionLevels/{id}/image",
                "Removed in V6. Use the PUT method instead. See #1922"
        );
        structureService.setPromotionLevelImage(promotionLevelId, new Document(
                file.getContentType(),
                file.getBytes()
        ));
    }

    /**
     * Bulk update of all promotion levels in other projects/branches and in predefined promotion levels,
     * following the model designed by the promotion level ID.
     *
     * @param promotionLevelId ID of the promotion level model
     * @return Result of the update
     */
    @PutMapping("promotionLevels/{promotionLevelId}/bulk")
    public ResponseEntity<Ack> bulkUpdate(@PathVariable ID promotionLevelId) {
        return ResponseEntity.ok(structureService.bulkUpdatePromotionLevels(promotionLevelId));
    }

}
