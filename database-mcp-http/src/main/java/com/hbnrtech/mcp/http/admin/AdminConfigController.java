package com.hbnrtech.mcp.http.admin;

import com.hbnrtech.mcp.http.admin.ConfigModels.BaseJdbcConfigPayload;
import com.hbnrtech.mcp.http.admin.ConfigModels.ChangePasswordPayload;
import com.hbnrtech.mcp.http.admin.ConfigModels.DatasourcePayload;
import com.hbnrtech.mcp.http.admin.ConfigModels.RuntimeSnapshot;
import com.hbnrtech.mcp.http.admin.ConfigModels.TestConnectionResult;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${database-mcp.http.admin-api-base-path:/admin/api}")
public class AdminConfigController {
   private final RuntimeConfigurationService runtimeConfigurationService;
   private final AdminCredentialService adminCredentialService;

   public AdminConfigController(
      RuntimeConfigurationService runtimeConfigurationService,
      AdminCredentialService adminCredentialService
   ) {
      this.runtimeConfigurationService = runtimeConfigurationService;
      this.adminCredentialService = adminCredentialService;
   }

   @GetMapping("/config")
   public RuntimeSnapshot config() {
      return this.runtimeConfigurationService.currentSnapshot();
   }

   @PutMapping("/base-configs/{baseConfigId}")
   public RuntimeSnapshot upsertBaseConfig(@PathVariable("baseConfigId") String baseConfigId, @RequestBody BaseJdbcConfigPayload payload) {
      return this.runtimeConfigurationService.upsertBaseConfig(baseConfigId, payload);
   }

   @DeleteMapping("/base-configs/{baseConfigId}")
   public RuntimeSnapshot deleteBaseConfig(@PathVariable("baseConfigId") String baseConfigId) {
      return this.runtimeConfigurationService.deleteBaseConfig(baseConfigId);
   }

   @PutMapping("/datasources/{datasourceId}")
   public RuntimeSnapshot upsertDatasource(@PathVariable("datasourceId") String datasourceId, @RequestBody DatasourcePayload payload) {
      return this.runtimeConfigurationService.upsertDatasource(datasourceId, payload);
   }

   @DeleteMapping("/datasources/{datasourceId}")
   public RuntimeSnapshot deleteDatasource(@PathVariable("datasourceId") String datasourceId) {
      return this.runtimeConfigurationService.deleteDatasource(datasourceId);
   }

   @GetMapping("/datasources/{datasourceId}/test")
   public TestConnectionResult testDatasource(@PathVariable("datasourceId") String datasourceId) {
      return this.runtimeConfigurationService.testDatasource(datasourceId);
   }

   @GetMapping("/health")
   public ResponseEntity<Void> health() {
      return ResponseEntity.noContent().build();
   }

   @GetMapping("/session")
   public Map<String, String> session() {
      return Map.of("username", this.adminCredentialService.username());
   }

   @PostMapping("/password")
   public Map<String, String> changePassword(@RequestBody ChangePasswordPayload payload) {
      this.adminCredentialService.changePassword(payload.currentPassword(), payload.newPassword());
      return Map.of("message", "密码已更新");
   }
}
