package com.hbnrtech.mcp.execution;

import com.hbnrtech.mcp.config.DatabaseType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The catalog of statements {@code db_execute} can be told to refuse, per database type.
 *
 * <p>The admin console renders this catalog as a checklist and a datasource stores the enabled rule
 * ids. Rules that are not part of {@link #defaultRuleIds(DatabaseType)} ship disabled: enabling one
 * is a deliberate, visible change to what the MCP server is allowed to run.
 */
public final class ExecuteBlacklistRules {
   private static final ExecuteBlacklistRule DROP_DATABASE = rule("drop_database", "DROP DATABASE", "删除数据库");
   private static final ExecuteBlacklistRule DROP_SCHEMA = rule("drop_schema", "DROP SCHEMA", "删除架构");
   private static final ExecuteBlacklistRule DROP_USER = rule("drop_user", "DROP USER", "删除用户");
   private static final ExecuteBlacklistRule DROP_ROLE = rule("drop_role", "DROP ROLE", "删除角色");
   private static final ExecuteBlacklistRule DROP_TABLESPACE = rule("drop_tablespace", "DROP TABLESPACE", "删除表空间");
   private static final ExecuteBlacklistRule ALTER_SYSTEM = rule("alter_system", "ALTER SYSTEM", "修改系统参数 / 强制日志切换");
   private static final ExecuteBlacklistRule ALTER_DATABASE = rule("alter_database", "ALTER DATABASE", "修改数据库");
   private static final ExecuteBlacklistRule CREATE_USER = rule("create_user", "CREATE USER", "创建用户");
   private static final ExecuteBlacklistRule GRANT = rule("grant", "GRANT", "授予权限");
   private static final ExecuteBlacklistRule REVOKE = rule("revoke", "REVOKE", "回收权限");
   private static final ExecuteBlacklistRule DROP_TABLE = rule("drop_table", "DROP TABLE", "删除表");
   private static final ExecuteBlacklistRule TRUNCATE_TABLE = rule("truncate_table", "TRUNCATE TABLE", "清空表");
   private static final ExecuteBlacklistRule DROP_VIEW = rule("drop_view", "DROP VIEW", "删除视图");
   private static final ExecuteBlacklistRule DROP_INDEX = rule("drop_index", "DROP INDEX", "删除索引");
   private static final ExecuteBlacklistRule ALTER_TABLE = rule("alter_table", "ALTER TABLE", "修改表结构");
   private static final ExecuteBlacklistRule DELETE = rule("delete", "DELETE", "DELETE 删除数据（也匹配 ON DELETE CASCADE）");
   private static final ExecuteBlacklistRule UPDATE = rule("update", "UPDATE", "UPDATE 更新数据（也匹配 SELECT FOR UPDATE）");
   private static final ExecuteBlacklistRule SHUTDOWN = rule("shutdown", "SHUTDOWN", "关闭数据库实例");
   private static final ExecuteBlacklistRule RESET_MASTER = rule("reset_master", "RESET MASTER", "重置二进制日志（主库）");
   private static final ExecuteBlacklistRule RESET_REPLICA = rule("reset_replica", "RESET REPLICA", "重置复制位点（从库）");
   private static final ExecuteBlacklistRule SET_GLOBAL = rule("set_global", "SET GLOBAL", "修改全局变量");
   private static final ExecuteBlacklistRule KILL = rule("kill", "KILL", "终止其他会话");

   private static final Map<DatabaseType, List<ExecuteBlacklistRule>> CATALOG = Map.of(
      DatabaseType.POSTGRES, List.of(
         DROP_DATABASE, DROP_SCHEMA, DROP_USER, DROP_ROLE, DROP_TABLESPACE, ALTER_SYSTEM,
         ALTER_DATABASE, CREATE_USER, GRANT, REVOKE,
         DROP_TABLE, TRUNCATE_TABLE, DROP_VIEW, DROP_INDEX, ALTER_TABLE, DELETE, UPDATE
      ),
      DatabaseType.ORACLE, List.of(
         DROP_DATABASE, DROP_USER, DROP_TABLESPACE, ALTER_SYSTEM,
         ALTER_DATABASE, CREATE_USER, GRANT, REVOKE,
         DROP_TABLE, TRUNCATE_TABLE, DROP_VIEW, DROP_INDEX, ALTER_TABLE, DELETE, UPDATE
      ),
      DatabaseType.MYSQL, List.of(
         DROP_DATABASE, DROP_SCHEMA, DROP_USER, RESET_MASTER, RESET_REPLICA, SHUTDOWN,
         ALTER_DATABASE, CREATE_USER, GRANT, REVOKE,
         DROP_TABLE, TRUNCATE_TABLE, DROP_VIEW, DROP_INDEX, ALTER_TABLE, DELETE, UPDATE, SET_GLOBAL, KILL
      )
   );

   /**
    * The rules every datasource starts with. These are the statements the server refused before the
    * blacklist became configurable, so backfilling them changes no existing behaviour.
    */
   private static final Map<DatabaseType, List<ExecuteBlacklistRule>> DEFAULTS = Map.of(
      DatabaseType.POSTGRES, List.of(DROP_DATABASE, DROP_SCHEMA, DROP_USER, DROP_ROLE, DROP_TABLESPACE, ALTER_SYSTEM),
      DatabaseType.ORACLE, List.of(DROP_DATABASE, DROP_USER, DROP_TABLESPACE, ALTER_SYSTEM),
      DatabaseType.MYSQL, List.of(DROP_DATABASE, DROP_SCHEMA, DROP_USER, RESET_MASTER, RESET_REPLICA, SHUTDOWN)
   );

   private ExecuteBlacklistRules() {
   }

   public static List<ExecuteBlacklistRule> forType(DatabaseType type) {
      return CATALOG.get(type);
   }

   public static List<String> defaultRuleIds(DatabaseType type) {
      return DEFAULTS.get(type).stream().map(ExecuteBlacklistRule::id).toList();
   }

   public static List<ExecuteBlacklistRule> defaults(DatabaseType type) {
      return resolve(type, defaultRuleIds(type));
   }

   /**
    * Maps stored rule ids back onto the catalog. Ids are returned in catalog order so the effective
    * blacklist is stable, and ids the catalog does not know are dropped — a rule removed from the
    * catalog must not turn into a rule that matches nothing, and must not break the datasource.
    */
   public static List<ExecuteBlacklistRule> resolve(DatabaseType type, Collection<String> ruleIds) {
      if (ruleIds == null) {
         return defaults(type);
      }

      Set<String> selected = new LinkedHashSet<>(ruleIds);
      List<ExecuteBlacklistRule> resolved = new ArrayList<>();
      for (ExecuteBlacklistRule rule : CATALOG.get(type)) {
         if (selected.contains(rule.id())) {
            resolved.add(rule);
         }
      }
      return resolved;
   }

   public static Set<String> knownRuleIds(DatabaseType type) {
      Set<String> ids = new LinkedHashSet<>();
      for (ExecuteBlacklistRule rule : CATALOG.get(type)) {
         ids.add(rule.id());
      }
      return ids;
   }

   private static ExecuteBlacklistRule rule(String id, String phrase, String label) {
      return new ExecuteBlacklistRule(id, phrase, label, phrase);
   }
}
