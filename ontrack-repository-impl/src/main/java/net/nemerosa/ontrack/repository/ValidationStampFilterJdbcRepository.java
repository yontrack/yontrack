package net.nemerosa.ontrack.repository;

import tools.jackson.databind.JsonNode;
import net.nemerosa.ontrack.model.Ack;
import net.nemerosa.ontrack.model.exceptions.ValidationStampFilterNameAlreadyDefinedException;
import net.nemerosa.ontrack.model.exceptions.ValidationStampFilterNotFoundException;
import net.nemerosa.ontrack.model.structure.Branch;
import net.nemerosa.ontrack.model.structure.ID;
import net.nemerosa.ontrack.model.structure.Project;
import net.nemerosa.ontrack.model.structure.ValidationStampFilter;
import net.nemerosa.ontrack.repository.support.AbstractJdbcRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Repository
public class ValidationStampFilterJdbcRepository extends AbstractJdbcRepository implements ValidationStampFilterRepository {

    private final StructureRepository structureRepository;

    @Autowired
    public ValidationStampFilterJdbcRepository(DataSource dataSource, StructureRepository structureRepository) {
        super(dataSource);
        this.structureRepository = structureRepository;
    }

    @Override
    public List<ValidationStampFilter> getGlobalValidationStampFilters() {
        return getNamedParameterJdbcTemplate().query(
                "SELECT * FROM VALIDATION_STAMP_FILTERS WHERE PROJECT IS NULL AND BRANCH IS NULL",
                noParams(),
                (ResultSet rs, int rowNum) -> toValidationStampFilter(rs, pid -> null, bid -> null)
        );
    }

    @Override
    public List<ValidationStampFilter> getProjectValidationStampFilters(Project project) {
        return getNamedParameterJdbcTemplate().query(
                "SELECT * FROM VALIDATION_STAMP_FILTERS WHERE PROJECT = :project AND BRANCH IS NULL",
                params("project", project.id()),
                (ResultSet rs, int rowNum) -> toValidationStampFilter(rs, pid -> project, bid -> null)
        );
    }

    @Override
    public List<ValidationStampFilter> getBranchValidationStampFilters(Branch branch) {
        return getNamedParameterJdbcTemplate().query(
                "SELECT * FROM VALIDATION_STAMP_FILTERS WHERE PROJECT IS NULL AND BRANCH = :branch",
                params("branch", branch.id()),
                (ResultSet rs, int rowNum) -> toValidationStampFilter(rs, pid -> null, bid -> branch)
        );
    }

    @Override
    public Optional<ValidationStampFilter> getValidationStampFilterByName(Branch branch, String name) {
        // Branch first
        Optional<ValidationStampFilter> o = getOptional(
                "SELECT * FROM VALIDATION_STAMP_FILTERS WHERE PROJECT IS NULL AND BRANCH = :branch AND NAME = :name",
                params("branch", branch.id()).addValue("name", name),
                (ResultSet rs, int rowNum) -> toValidationStampFilter(rs, pid -> null, bid -> branch)
        );
        // ... then project
        if (!o.isPresent()) {
            o = getOptional(
                    "SELECT * FROM VALIDATION_STAMP_FILTERS WHERE PROJECT = :project AND BRANCH IS NULL AND NAME = :name",
                    params("project", branch.getProject().id()).addValue("name", name),
                    (ResultSet rs, int rowNum) -> toValidationStampFilter(rs, pid -> branch.getProject(), bid -> null)
            );
        }
        // ... the global
        if (!o.isPresent()) {
            o = getOptional(
                    "SELECT * FROM VALIDATION_STAMP_FILTERS WHERE PROJECT IS NULL AND BRANCH IS NULL AND NAME = :name",
                    params("name", name),
                    (ResultSet rs, int rowNum) -> toValidationStampFilter(rs, pid -> null, bid -> null)
            );
        }
        // OK
        return o;
    }

    @Override
    public ValidationStampFilter newValidationStampFilter(ValidationStampFilter filter) {
        // Check unicity
        checkUnicity(filter);
        // Creation
        int id = dbCreate(
                "INSERT INTO VALIDATION_STAMP_FILTERS(NAME, PROJECT, BRANCH, VSNAMES) VALUES (:name, :project, :branch, :vsNames)",
                params("name", filter.getName())
                        .addValue("project", filter.getProject() != null ? filter.getProject().id() : null)
                        .addValue("branch", filter.getBranch() != null ? filter.getBranch().id() : null)
                        .addValue("vsNames", saveVsNames(filter.getVsNames()))
        );
        // Returns with ID
        return filter.withId(id(id));
    }

    /**
     * Note: in H2, null columns are not taken into account in the index
     */
    private void checkUnicity(ValidationStampFilter filter) {
        // Check project vs branch
        if (filter.getProject() != null && filter.getBranch() != null) {
            throw new IllegalStateException("Filter cannot be associated with both a project and a branch.");
        }
        // Gets the existing filter for the name in this scope
        MapSqlParameterSource params = params("name", filter.getName())
                .addValue("project", filter.getProject() != null ? filter.getProject().id() : null)
                .addValue("branch", filter.getBranch() != null ? filter.getBranch().id() : null)
                .addValue("id", filter.getId() != null && filter.getId().isSet() ? filter.id() : null);
        String sql;
        // Branch
        if (filter.getBranch() != null) {
            sql = "SELECT ID FROM VALIDATION_STAMP_FILTERS WHERE PROJECT IS NULL AND BRANCH = :branch AND NAME = :name";
        }
        // Project
        else if (filter.getProject() != null) {
            sql = "SELECT ID FROM VALIDATION_STAMP_FILTERS WHERE PROJECT = :project AND BRANCH IS NULL AND NAME = :name";
        }
        // Global
        else {
            sql = "SELECT ID FROM VALIDATION_STAMP_FILTERS WHERE PROJECT IS NULL AND BRANCH IS NULL AND NAME = :name";
        }
        // ID
        if (filter.getId() != null && filter.getId().isSet()) {
            sql += " AND ID <> :id";
        }
        // Check
        Optional<Integer> o = getOptional(
                sql,
                params,
                Integer.class
        );
        if (o.isPresent()) {
            throw new ValidationStampFilterNameAlreadyDefinedException(filter.getName());
        }
    }

    @Override
    public void saveValidationStampFilter(ValidationStampFilter filter) {
        checkUnicity(filter);
        getNamedParameterJdbcTemplate().update(
                "UPDATE VALIDATION_STAMP_FILTERS SET NAME = :name, PROJECT = :project, BRANCH = :branch, VSNAMES = :vsNames WHERE ID = :id",
                params("name", filter.getName())
                        .addValue("project", filter.getProject() != null ? filter.getProject().id() : null)
                        .addValue("branch", filter.getBranch() != null ? filter.getBranch().id() : null)
                        .addValue("vsNames", saveVsNames(filter.getVsNames()))
                        .addValue("id", filter.id())
        );
    }

    @Override
    public Ack deleteValidationStampFilter(ID filterId) {
        return Ack.one(
                getNamedParameterJdbcTemplate().update(
                        "DELETE FROM VALIDATION_STAMP_FILTERS WHERE ID = :id",
                        params("id", filterId.getValue())
                )
        );
    }

    @Override
    public ValidationStampFilter getValidationStampFilter(ID filterId) {
        try {
            return getNamedParameterJdbcTemplate().queryForObject(
                    "SELECT * FROM VALIDATION_STAMP_FILTERS WHERE ID = :id",
                    params("id", filterId.getValue()),
                    (rs, rowNum) -> toValidationStampFilter(
                            rs,
                            pid -> pid != null ? structureRepository.getProject(ID.of(pid)) : null,
                            bid -> bid != null ? structureRepository.getBranch(ID.of(bid)) : null
                    )
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ValidationStampFilterNotFoundException(filterId);
        }
    }

    @Override
    public ValidationStampFilter shareValidationStampFilter(ValidationStampFilter filter, Project project) {
        ValidationStampFilter newFilter = filter.withProject(project).withBranch(null);
        saveValidationStampFilter(newFilter);
        return newFilter;
    }

    @Override
    public ValidationStampFilter shareValidationStampFilter(ValidationStampFilter filter) {
        ValidationStampFilter newFilter = filter.withProject(null).withBranch(null);
        saveValidationStampFilter(newFilter);
        return newFilter;
    }

    private ValidationStampFilter toValidationStampFilter(
            ResultSet rs,
            Function<Integer, Project> projectLoader,
            Function<Integer, Branch> branchLoader) throws SQLException {
        return new ValidationStampFilter(
                id(rs),
                rs.getString("NAME"),
                loadVsNames(rs.getString("VSNAMES")),
                projectLoader.apply(rs.getObject("PROJECT", Integer.class)),
                branchLoader.apply(rs.getObject("BRANCH", Integer.class))
        );
    }

    private List<String> loadVsNames(String vsNames) {
        if (StringUtils.isNotBlank(vsNames)) {
            // Parses as JSON list
            JsonNode json = readJson(vsNames);
            if (json.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode node : json) {
                    values.add(node.asText());
                }
                return values;
            } else {
                return Collections.emptyList();
            }
        } else {
            return Collections.emptyList();
        }
    }

    private String saveVsNames(List<String> vsNames) {
        if (vsNames == null) {
            return null;
        } else {
            return writeJson(vsNames);
        }
    }
}
