package com.example.mcp;

public final class DatabaseMcpServer {
   private DatabaseMcpServer() {
   }

   public static void main(String[] args) {
      // 对外暴露稳定的主入口，实际启动逻辑下沉到 bootstrap 包中。
      com.example.mcp.bootstrap.DatabaseMcpServer.main(args);
   }
}
