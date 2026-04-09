package com.elysium.code.memory

object InitialKnowledge {
    fun getDefaultKnowledge(): List<KnowledgeItem> {
        return listOf(
            KnowledgeItem(
                id = "k_arch_001",
                title = "Clean Architecture Core",
                content = "Code must be decoupled. Use Repository pattern for data access, UseCases/Interactors for business logic, and Presentation layer for UI. Depend on abstractions, not concretions.",
                category = KnowledgeCategory.ARCHITECTURE,
                tags = listOf("architecture", "clean-architecture", "solid")
            ),
            KnowledgeItem(
                id = "k_solid_001",
                title = "SOLID Principles Checklist",
                content = "S: Single Responsibility. O: Open/Closed. L: Liskov Substitution. I: Interface Segregation. D: Dependency Inversion. Always adhere to these principles for scalable software.",
                category = KnowledgeCategory.BEST_PRACTICE,
                tags = listOf("solid", "best-practice", "oop")
            ),
            KnowledgeItem(
                id = "k_jni_001",
                title = "JNI and Native Integration",
                content = "When bridging C++ and Kotlin, use careful memory management. Always detatch JNIEnv threads if spawned natively. Prefer primitive arrays over object arrays for cross-language latency optimization.",
                category = KnowledgeCategory.ARCHITECTURE,
                tags = listOf("jni", "c++", "kotlin", "performance")
            ),
            KnowledgeItem(
                id = "k_sec_001",
                title = "Android W^X & Security",
                content = "Modern Android enforces Write XOR Execute (W^X). Memory pages cannot be both writable and executable. Use PROT_EXEC mapped files from assets rather than allocating executable memory manually for PTY environments.",
                category = KnowledgeCategory.DEVOPS,
                tags = listOf("security", "android", "w^x", "memory")
            ),
            KnowledgeItem(
                id = "k_perf_001",
                title = "Concurrency & Coroutines",
                content = "Do NOT block the Main Thread. Use Dispatchers.IO for networking/file IO, Dispatchers.Default for CPU heavy tasks. Flow is preferred over LiveData for reactive architectures.",
                category = KnowledgeCategory.LANGUAGE_SPECIFIC,
                tags = listOf("kotlin", "coroutines", "flow", "concurrency")
            ),
            KnowledgeItem(
                id = "k_mcp_omni_001",
                title = "Omni CLI Execution (MCP Bypass)",
                content = "To bypass the 100-tool MCP limit on Android, do NOT rely on Node/Python MCP servers for Vercel, Stripe, or Supabase. Instead, use the 'execute_command' tool to run their native CLI versions directly on the terminal (e.g., 'npx vercel deploy', 'supabase start').",
                category = KnowledgeCategory.TOOL_USAGE,
                tags = listOf("mcp", "cli", "bypass", "omni")
            )
        )
    }
}
