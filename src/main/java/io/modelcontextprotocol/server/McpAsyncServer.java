package io.modelcontextprotocol.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpClientSession;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.server.transport.WebFluxSseServerTransportProvider;
import io.modelcontextprotocol.util.Utils;
import io.secretarymcp.registry.UserSecretaryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The Model Context Protocol (MCP) server implementation that provides asynchronous
 * communication using Project Reactor's Mono and Flux types.
 *
 * <p>
 * This server implements the MCP specification, enabling AI models to expose tools,
 * resources, and prompts through a standardized interface. Key features include:
 * <ul>
 * <li>Asynchronous communication using reactive programming patterns
 * <li>Dynamic tool registration and management
 * <li>Resource handling with URI-based addressing
 * <li>Prompt template management
 * <li>Real-time client notifications for state changes
 * <li>Structured logging with configurable severity levels
 * <li>Support for client-side AI model sampling
 * </ul>
 *
 * <p>
 * The server follows a lifecycle:
 * <ol>
 * <li>Initialization - Accepts client connections and negotiates capabilities
 * <li>Normal Operation - Handles client requests and sends notifications
 * <li>Graceful Shutdown - Ensures clean connection termination
 * </ol>
 *
 * <p>
 * This implementation uses Project Reactor for non-blocking operations, making it
 * suitable for high-throughput scenarios and reactive applications. All operations return
 * Mono or Flux types that can be composed into reactive pipelines.
 *
 * <p>
 * The server supports runtime modification of its capabilities through methods like
 * {@link #addTool}, {@link #addResource}, and {@link #addPrompt}, automatically notifying
 * connected clients of changes when configured to do so.
 *
 * @author Christian Tzolov
 * @author Dariusz Jędrzejczyk
 * @see McpServer
 * @see McpSchema
 * @see McpClientSession
 */
public class McpAsyncServer {

	private static final Logger logger = LoggerFactory.getLogger(McpAsyncServer.class);

	private final McpAsyncServer delegate;

	McpAsyncServer() {
		this.delegate = null;
	}

	/**
	 * Create a new McpAsyncServer with the given transport provider and capabilities.
	 * @param mcpTransportProvider The transport layer implementation for MCP
	 * communication.
	 * @param features The MCP server supported features.
	 * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
	 * @param userSecretaryRegistry The registry that maps users to secretaries
	 */
	McpAsyncServer(McpServerTransportProvider mcpTransportProvider, ObjectMapper objectMapper,
			McpServerFeatures.Async features, UserSecretaryRegistry userSecretaryRegistry) {
		this.delegate = new AsyncServerImpl(mcpTransportProvider, objectMapper, features, userSecretaryRegistry);
	}

	/**
	 * Create a new McpAsyncServer with the given transport provider and capabilities.
	 * @param mcpTransportProvider The transport layer implementation for MCP
	 * communication.
	 * @param features The MCP server supported features.
	 * @param objectMapper The ObjectMapper to use for JSON serialization/deserialization
	 */
	McpAsyncServer(McpServerTransportProvider mcpTransportProvider, ObjectMapper objectMapper,
			McpServerFeatures.Async features) {
		this(mcpTransportProvider, objectMapper, features, null);
	}

	/**
	 * Get the server capabilities that define the supported features and functionality.
	 * @return The server capabilities
	 */
	public McpSchema.ServerCapabilities getServerCapabilities() {
		return this.delegate.getServerCapabilities();
	}

	/**
	 * Get the server implementation information.
	 * @return The server implementation details
	 */
	public McpSchema.Implementation getServerInfo() {
		return this.delegate.getServerInfo();
	}

	/**
	 * Gracefully closes the server, allowing any in-progress operations to complete.
	 * @return A Mono that completes when the server has been closed
	 */
	public Mono<Void> closeGracefully() {
		return this.delegate.closeGracefully();
	}

	/**
	 * Close the server immediately.
	 */
	public void close() {
		this.delegate.close();
	}

	// ---------------------------------------
	// Tool Management
	// ---------------------------------------
	/**
	 * Add a new tool specification at runtime.
	 * @param toolSpecification The tool specification to add
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> addTool(McpServerFeatures.AsyncToolSpecification toolSpecification) {
		return this.delegate.addTool(toolSpecification);
	}

	/**
	 * Remove a tool handler at runtime.
	 * @param toolName The name of the tool handler to remove
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> removeTool(String toolName) {
		return this.delegate.removeTool(toolName);
	}

	/**
	 * Notifies clients that the list of available tools has changed.
	 * @return A Mono that completes when all clients have been notified
	 */
	public Mono<Void> notifyToolsListChanged() {
		return this.delegate.notifyToolsListChanged();
	}

	// ---------------------------------------
	// Resource Management
	// ---------------------------------------
	/**
	 * Add a new resource handler at runtime.
	 * @param resourceHandler The resource handler to add
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> addResource(McpServerFeatures.AsyncResourceSpecification resourceHandler) {
		return this.delegate.addResource(resourceHandler);
	}

	/**
	 * Remove a resource handler at runtime.
	 * @param resourceUri The URI of the resource handler to remove
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> removeResource(String resourceUri) {
		return this.delegate.removeResource(resourceUri);
	}

	/**
	 * Notifies clients that the list of available resources has changed.
	 * @return A Mono that completes when all clients have been notified
	 */
	public Mono<Void> notifyResourcesListChanged() {
		return this.delegate.notifyResourcesListChanged();
	}

	// ---------------------------------------
	// Prompt Management
	// ---------------------------------------
	/**
	 * Add a new prompt handler at runtime.
	 * @param promptSpecification The prompt handler to add
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> addPrompt(McpServerFeatures.AsyncPromptSpecification promptSpecification) {
		return this.delegate.addPrompt(promptSpecification);
	}

	/**
	 * Remove a prompt handler at runtime.
	 * @param promptName The name of the prompt handler to remove
	 * @return Mono that completes when clients have been notified of the change
	 */
	public Mono<Void> removePrompt(String promptName) {
		return this.delegate.removePrompt(promptName);
	}

	/**
	 * Notifies clients that the list of available prompts has changed.
	 * @return A Mono that completes when all clients have been notified
	 */
	public Mono<Void> notifyPromptsListChanged() {
		return this.delegate.notifyPromptsListChanged();
	}

	// ---------------------------------------
	// Logging Management
	// ---------------------------------------

	/**
	 * Send a logging message notification to all connected clients. Messages below the
	 * current minimum logging level will be filtered out.
	 * @param loggingMessageNotification The logging message to send
	 * @return A Mono that completes when the notification has been sent
	 */
	public Mono<Void> loggingNotification(LoggingMessageNotification loggingMessageNotification) {
		return this.delegate.loggingNotification(loggingMessageNotification);
	}

	// ---------------------------------------
	// Sampling
	// ---------------------------------------
	/**
	 * This method is package-private and used for test only. Should not be called by user
	 * code.
	 * @param protocolVersions the Client supported protocol versions.
	 */
	void setProtocolVersions(List<String> protocolVersions) {
		this.delegate.setProtocolVersions(protocolVersions);
	}

	/**
	 * 注册用户与Secretary的关联
	 * @param userId 用户ID
	 * @param secretaryName Secretary名称
	 * @return 操作完成的Mono
	 */
	public Mono<Void> registerUserSecretary(String userId, String secretaryName) {
		if (userId == null || secretaryName == null) {
			return Mono.error(new McpError("User ID and Secretary name must not be null"));
		}
		
		return this.delegate.registerUserSecretary(userId, secretaryName)
			.doOnSuccess(v -> logger.info("Registered user [{}] with secretary [{}]", userId, secretaryName));
	}

	/**
	 * 移除用户与Secretary的关联
	 * @param userId 用户ID
	 * @return 操作完成的Mono
	 */
	public Mono<Void> unregisterUser(String userId) {
		if (userId == null) {
			return Mono.error(new McpError("User ID must not be null"));
		}
		
		return this.delegate.unregisterUser(userId)
			.doOnSuccess(v -> logger.info("Unregistered all secretaries for user [{}]", userId));
	}

	/**
	 * 获取用户关联的Secretary名称
	 * @param userId 用户ID
	 * @return Secretary名称
	 */
	public Mono<String> getSecretaryForUser(String userId) {
		if (userId == null) {
			return Mono.error(new McpError("User ID must not be null"));
		}
		return this.delegate.getSecretaryForUser(userId);
	}

	/**
	 * 获取用户关联的所有秘书名称
	 * @param userId 用户ID
	 * @return 秘书名称列表
	 */
	public Flux<String> getSecretariesForUser(String userId) {
		if (userId == null) {
			return Flux.error(new McpError("User ID must not be null"));
		}
		return this.delegate.getSecretariesForUser(userId);
	}


	/**
	 * 解除用户与特定秘书的关联
	 * @param userId 用户ID
	 * @param secretaryName 秘书名称
	 * @return 操作完成的Mono
	 */
	public Mono<Void> unregisterUserSecretary(String userId, String secretaryName) {
		if (userId == null || secretaryName == null) {
			return Mono.error(new McpError("User ID and Secretary name must not be null"));
		}
		
		return this.delegate.unregisterUserSecretary(userId, secretaryName)
			.doOnSuccess(v -> logger.info("Unregistered user [{}] from secretary [{}]", userId, secretaryName));
	}

	/**
	 * 获取所有用户-秘书映射关系
	 * @return 映射关系
	 */
	public Mono<Map<String, List<String>>> getAllUserSecretaryMappings() {
		return this.delegate.getAllUserSecretaryMappings();
	}

	private static class AsyncServerImpl extends McpAsyncServer {

		private final McpServerTransportProvider mcpTransportProvider;

		private final ObjectMapper objectMapper;

		private final McpSchema.ServerCapabilities serverCapabilities;

		private final McpSchema.Implementation serverInfo;

		private final CopyOnWriteArrayList<McpServerFeatures.AsyncToolSpecification> tools = new CopyOnWriteArrayList<>();

		private final CopyOnWriteArrayList<McpSchema.ResourceTemplate> resourceTemplates = new CopyOnWriteArrayList<>();

		private final ConcurrentHashMap<String, McpServerFeatures.AsyncResourceSpecification> resources = new ConcurrentHashMap<>();

		private final ConcurrentHashMap<String, McpServerFeatures.AsyncPromptSpecification> prompts = new ConcurrentHashMap<>();

		private LoggingLevel minLoggingLevel = LoggingLevel.DEBUG;

		private List<String> protocolVersions = List.of(McpSchema.LATEST_PROTOCOL_VERSION);

		private final UserSecretaryRegistry userSecretaryRegistry;

		AsyncServerImpl(McpServerTransportProvider mcpTransportProvider, ObjectMapper objectMapper,
				McpServerFeatures.Async features, UserSecretaryRegistry userSecretaryRegistry) {
			this.mcpTransportProvider = mcpTransportProvider;
			this.objectMapper = objectMapper;
			this.serverInfo = features.serverInfo();
			this.serverCapabilities = features.serverCapabilities();
			this.tools.addAll(features.tools());
			this.resources.putAll(features.resources());
			this.resourceTemplates.addAll(features.resourceTemplates());
			this.prompts.putAll(features.prompts());
			this.userSecretaryRegistry = userSecretaryRegistry;

			Map<String, McpServerSession.RequestHandler<?>> requestHandlers = new HashMap<>();

			// Initialize request handlers for standard MCP methods

			// Ping MUST respond with an empty data, but not NULL response.
			requestHandlers.put(McpSchema.METHOD_PING, (exchange, params) -> Mono.just(Map.of()));

			// Add tools API handlers if the tool capability is enabled
			if (this.serverCapabilities.tools() != null) {
				requestHandlers.put(McpSchema.METHOD_TOOLS_LIST, toolsListRequestHandler());
				requestHandlers.put(McpSchema.METHOD_TOOLS_CALL, toolsCallRequestHandler());
			}

			// Add resources API handlers if provided
			if (this.serverCapabilities.resources() != null) {
				requestHandlers.put(McpSchema.METHOD_RESOURCES_LIST, resourcesListRequestHandler());
				requestHandlers.put(McpSchema.METHOD_RESOURCES_READ, resourcesReadRequestHandler());
				requestHandlers.put(McpSchema.METHOD_RESOURCES_TEMPLATES_LIST, resourceTemplateListRequestHandler());
			}

			// Add prompts API handlers if provider exists
			if (this.serverCapabilities.prompts() != null) {
				requestHandlers.put(McpSchema.METHOD_PROMPT_LIST, promptsListRequestHandler());
				requestHandlers.put(McpSchema.METHOD_PROMPT_GET, promptsGetRequestHandler());
			}

			// Add logging API handlers if the logging capability is enabled
			if (this.serverCapabilities.logging() != null) {
				requestHandlers.put(McpSchema.METHOD_LOGGING_SET_LEVEL, setLoggerRequestHandler());
			}

			Map<String, McpServerSession.NotificationHandler> notificationHandlers = new HashMap<>();

			notificationHandlers.put(McpSchema.METHOD_NOTIFICATION_INITIALIZED, (exchange, params) -> Mono.empty());

			List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers = features
				.rootsChangeConsumers();

			if (Utils.isEmpty(rootsChangeConsumers)) {
				rootsChangeConsumers = List.of((exchange,
						roots) -> Mono.fromRunnable(() -> logger.warn(
								"Roots list changed notification, but no consumers provided. Roots list changed: {}",
								roots)));
			}

			notificationHandlers.put(McpSchema.METHOD_NOTIFICATION_ROOTS_LIST_CHANGED,
					asyncRootsListChangedNotificationHandler(rootsChangeConsumers));

			mcpTransportProvider.setSessionFactory(transport -> {
				// 尝试从传输对象获取userId
				String userId = null;
				if (transport instanceof WebFluxSseServerTransportProvider.WebFluxMcpSessionTransport) {
					// 获取传输对象中的userId
					userId = ((WebFluxSseServerTransportProvider.WebFluxMcpSessionTransport) transport).getUserId();
					logger.info("从传输层获取到userId: {}", userId);
				}
				
				// 生成会话ID，使用下划线代替冒号作为分隔符
				String uuid = UUID.randomUUID().toString();
				String sessionId = (userId != null && !userId.isEmpty()) 
					? userId + "_" + uuid     // 使用下划线分隔
					: uuid;
				logger.info("创建会话: sessionId={}, userId={}", sessionId, userId);
				
				// 使用自定义会话ID创建会话
				return new McpServerSession(sessionId, transport,
						this::asyncInitializeRequestHandler, Mono::empty, requestHandlers, notificationHandlers);
			});
		}

		// ---------------------------------------
		// Lifecycle Management
		// ---------------------------------------
		private Mono<McpSchema.InitializeResult> asyncInitializeRequestHandler(
				McpSchema.InitializeRequest initializeRequest) {
			return Mono.defer(() -> {
				logger.info("Client initialize request - Protocol: {}, Capabilities: {}, Info: {}",
						initializeRequest.protocolVersion(), initializeRequest.capabilities(),
						initializeRequest.clientInfo());

				// The server MUST respond with the highest protocol version it supports
				// if
				// it does not support the requested (e.g. Client) version.
				String serverProtocolVersion = this.protocolVersions.get(this.protocolVersions.size() - 1);

				if (this.protocolVersions.contains(initializeRequest.protocolVersion())) {
					// If the server supports the requested protocol version, it MUST
					// respond
					// with the same version.
					serverProtocolVersion = initializeRequest.protocolVersion();
				}
				else {
					logger.warn(
							"Client requested unsupported protocol version: {}, so the server will sugggest the {} version instead",
							initializeRequest.protocolVersion(), serverProtocolVersion);
				}

				return Mono.just(new McpSchema.InitializeResult(serverProtocolVersion, this.serverCapabilities,
						this.serverInfo, null));
			});
		}

		public McpSchema.ServerCapabilities getServerCapabilities() {
			return this.serverCapabilities;
		}

		public McpSchema.Implementation getServerInfo() {
			return this.serverInfo;
		}

		@Override
		public Mono<Void> closeGracefully() {
			return this.mcpTransportProvider.closeGracefully();
		}

		@Override
		public void close() {
			this.mcpTransportProvider.close();
		}

		private McpServerSession.NotificationHandler asyncRootsListChangedNotificationHandler(
				List<BiFunction<McpAsyncServerExchange, List<McpSchema.Root>, Mono<Void>>> rootsChangeConsumers) {
			return (exchange, params) -> exchange.listRoots()
				.flatMap(listRootsResult -> Flux.fromIterable(rootsChangeConsumers)
					.flatMap(consumer -> consumer.apply(exchange, listRootsResult.roots()))
					.onErrorResume(error -> {
						logger.error("Error handling roots list change notification", error);
						return Mono.empty();
					})
					.then());
		}

		// ---------------------------------------
		// Tool Management
		// ---------------------------------------

		@Override
		public Mono<Void> addTool(McpServerFeatures.AsyncToolSpecification toolSpecification) {
			if (toolSpecification == null) {
				return Mono.error(new McpError("Tool specification must not be null"));
			}
			if (toolSpecification.tool() == null) {
				return Mono.error(new McpError("Tool must not be null"));
			}
			if (toolSpecification.call() == null) {
				return Mono.error(new McpError("Tool call handler must not be null"));
			}
			if (this.serverCapabilities.tools() == null) {
				return Mono.error(new McpError("Server must be configured with tool capabilities"));
			}

			return Mono.defer(() -> {
				// Check for duplicate tool names
				if (this.tools.stream().anyMatch(th -> th.tool().name().equals(toolSpecification.tool().name()))) {
					return Mono
						.error(new McpError("Tool with name '" + toolSpecification.tool().name() + "' already exists"));
				}

				this.tools.add(toolSpecification);
				logger.debug("Added tool handler: {}", toolSpecification.tool().name());

				if (this.serverCapabilities.tools().listChanged()) {
					return notifyToolsListChanged();
				}
				return Mono.empty();
			});
		}

		@Override
		public Mono<Void> removeTool(String toolName) {
			if (toolName == null) {
				return Mono.error(new McpError("Tool name must not be null"));
			}
			if (this.serverCapabilities.tools() == null) {
				return Mono.error(new McpError("Server must be configured with tool capabilities"));
			}

			return Mono.defer(() -> {
				boolean removed = this.tools
					.removeIf(toolSpecification -> toolSpecification.tool().name().equals(toolName));
				if (removed) {
					logger.debug("Removed tool handler: {}", toolName);
					if (this.serverCapabilities.tools().listChanged()) {
						return notifyToolsListChanged();
					}
					return Mono.empty();
				}
				return Mono.error(new McpError("Tool with name '" + toolName + "' not found"));
			});
		}

		@Override
		public Mono<Void> notifyToolsListChanged() {
			return this.mcpTransportProvider.notifyClients(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED, null);
		}

		private McpServerSession.RequestHandler<McpSchema.ListToolsResult> toolsListRequestHandler() {
			return (exchange, params) -> {
				// 获取会话ID
				String sessionId = exchange.getSession().getId();
				
				// 提取用户ID
				String userId = userSecretaryRegistry != null ? 
					userSecretaryRegistry.extractUserIdFromSessionId(sessionId) : null;
				
				// 获取所有工具
				List<Tool> allTools = this.tools.stream()
					.map(McpServerFeatures.AsyncToolSpecification::tool)
					.toList();
				
				// 如果没有用户注册表或用户ID为空，直接过滤出系统工具即可
				if (userSecretaryRegistry == null || userId == null) {
					allTools = allTools.stream()
						.filter(tool -> tool.name().contains("system_"))
						.toList();
					return Mono.just(new McpSchema.ListToolsResult(allTools, null));
				}
				
				// 如果有用户ID，获取用户关联的所有Secretary
				List<Tool> finalAllTools = allTools;
				return userSecretaryRegistry.getSecretariesForUser(userId)
					.collectList()
					.map(secretaryNames -> {
						// 用户有关联的Secretary，过滤工具列表
						List<Tool> filteredTools = finalAllTools.stream()
							.filter(tool -> {
								// 系统工具对所有用户可见
								if (tool.name().contains("system_")) {
									return true;
								}
								
								// 检查工具是否属于该用户的任何一个Secretary
								return secretaryNames.stream()
									.anyMatch(secretaryName -> 
										userSecretaryRegistry.isToolBelongsToSecretary(tool.name(), secretaryName));
							})
							.toList();
						
						return new McpSchema.ListToolsResult(filteredTools, null);
					})
					.defaultIfEmpty(new McpSchema.ListToolsResult(
						// 如果用户没有关联Secretary，只返回系统工具
						allTools.stream()
							.filter(tool -> tool.name().contains("system_"))
							.toList(), 
						null
					));
			};
		}

		private McpServerSession.RequestHandler<CallToolResult> toolsCallRequestHandler() {
			return (exchange, params) -> {
				McpSchema.CallToolRequest callToolRequest = objectMapper.convertValue(params,
						new TypeReference<McpSchema.CallToolRequest>() {
						});
				
				// 获取会话ID并提取用户ID
				String sessionId = exchange.getSession().getId();
				String userId = userSecretaryRegistry != null ? 
					userSecretaryRegistry.extractUserIdFromSessionId(sessionId) : null;
				
				String toolName = callToolRequest.name();
				
				// 系统工具总是可访问的
				boolean isSystemTool = toolName.contains("system_");
				
				// 工具权限检查 - 使用新的检查方法支持多秘书
				if (!isSystemTool && userSecretaryRegistry != null && userId != null) {
					return userSecretaryRegistry.getSecretariesForUser(userId)
						.collectList()
						.flatMap(secretaryNames -> {
							// 检查工具是否属于用户的任何一个秘书
							boolean canAccess = secretaryNames.stream()
								.anyMatch(secretaryName -> 
									userSecretaryRegistry.isToolBelongsToSecretary(toolName, secretaryName));
							
							if (!canAccess) {
								return Mono.error(new McpError("Access denied to tool: " + toolName));
							}
							
							// 继续处理工具调用
							Optional<McpServerFeatures.AsyncToolSpecification> toolSpecification = tools.stream()
								.filter(tr -> toolName.equals(tr.tool().name()))
								.findAny();
							
							if (toolSpecification.isEmpty()) {
								return Mono.error(new McpError("Tool not found: " + toolName));
							}
							
							return toolSpecification.get().call().apply(exchange, callToolRequest.arguments());
						});
				}
				
				// 查找工具并调用
				Optional<McpServerFeatures.AsyncToolSpecification> toolSpecification = this.tools.stream()
					.filter(tr -> toolName.equals(tr.tool().name()))
					.findAny();
				
				if (toolSpecification.isEmpty()) {
					return Mono.error(new McpError("Tool not found: " + toolName));
				}
				
				return toolSpecification.get().call().apply(exchange, callToolRequest.arguments());
			};
		}

		// ---------------------------------------
		// Resource Management
		// ---------------------------------------

		@Override
		public Mono<Void> addResource(McpServerFeatures.AsyncResourceSpecification resourceSpecification) {
			if (resourceSpecification == null || resourceSpecification.resource() == null) {
				return Mono.error(new McpError("Resource must not be null"));
			}

			if (this.serverCapabilities.resources() == null) {
				return Mono.error(new McpError("Server must be configured with resource capabilities"));
			}

			return Mono.defer(() -> {
				if (this.resources.putIfAbsent(resourceSpecification.resource().uri(), resourceSpecification) != null) {
					return Mono.error(new McpError(
							"Resource with URI '" + resourceSpecification.resource().uri() + "' already exists"));
				}
				logger.debug("Added resource handler: {}", resourceSpecification.resource().uri());
				if (this.serverCapabilities.resources().listChanged()) {
					return notifyResourcesListChanged();
				}
				return Mono.empty();
			});
		}

		@Override
		public Mono<Void> removeResource(String resourceUri) {
			if (resourceUri == null) {
				return Mono.error(new McpError("Resource URI must not be null"));
			}
			if (this.serverCapabilities.resources() == null) {
				return Mono.error(new McpError("Server must be configured with resource capabilities"));
			}

			return Mono.defer(() -> {
				McpServerFeatures.AsyncResourceSpecification removed = this.resources.remove(resourceUri);
				if (removed != null) {
					logger.debug("Removed resource handler: {}", resourceUri);
					if (this.serverCapabilities.resources().listChanged()) {
						return notifyResourcesListChanged();
					}
					return Mono.empty();
				}
				return Mono.error(new McpError("Resource with URI '" + resourceUri + "' not found"));
			});
		}

		@Override
		public Mono<Void> notifyResourcesListChanged() {
			return this.mcpTransportProvider.notifyClients(McpSchema.METHOD_NOTIFICATION_RESOURCES_LIST_CHANGED, null);
		}

		private McpServerSession.RequestHandler<McpSchema.ListResourcesResult> resourcesListRequestHandler() {
			return (exchange, params) -> {
				var resourceList = this.resources.values()
					.stream()
					.map(McpServerFeatures.AsyncResourceSpecification::resource)
					.toList();
				return Mono.just(new McpSchema.ListResourcesResult(resourceList, null));
			};
		}

		private McpServerSession.RequestHandler<McpSchema.ListResourceTemplatesResult> resourceTemplateListRequestHandler() {
			return (exchange, params) -> Mono
				.just(new McpSchema.ListResourceTemplatesResult(this.resourceTemplates, null));

		}

		private McpServerSession.RequestHandler<McpSchema.ReadResourceResult> resourcesReadRequestHandler() {
			return (exchange, params) -> {
				McpSchema.ReadResourceRequest resourceRequest = objectMapper.convertValue(params,
						new TypeReference<McpSchema.ReadResourceRequest>() {
						});
				var resourceUri = resourceRequest.uri();
				McpServerFeatures.AsyncResourceSpecification specification = this.resources.get(resourceUri);
				if (specification != null) {
					return specification.readHandler().apply(exchange, resourceRequest);
				}
				return Mono.error(new McpError("Resource not found: " + resourceUri));
			};
		}

		// ---------------------------------------
		// Prompt Management
		// ---------------------------------------

		@Override
		public Mono<Void> addPrompt(McpServerFeatures.AsyncPromptSpecification promptSpecification) {
			if (promptSpecification == null) {
				return Mono.error(new McpError("Prompt specification must not be null"));
			}
			if (this.serverCapabilities.prompts() == null) {
				return Mono.error(new McpError("Server must be configured with prompt capabilities"));
			}

			return Mono.defer(() -> {
				McpServerFeatures.AsyncPromptSpecification specification = this.prompts
					.putIfAbsent(promptSpecification.prompt().name(), promptSpecification);
				if (specification != null) {
					return Mono.error(new McpError(
							"Prompt with name '" + promptSpecification.prompt().name() + "' already exists"));
				}

				logger.debug("Added prompt handler: {}", promptSpecification.prompt().name());

				// Servers that declared the listChanged capability SHOULD send a
				// notification,
				// when the list of available prompts changes
				if (this.serverCapabilities.prompts().listChanged()) {
					return notifyPromptsListChanged();
				}
				return Mono.empty();
			});
		}

		@Override
		public Mono<Void> removePrompt(String promptName) {
			if (promptName == null) {
				return Mono.error(new McpError("Prompt name must not be null"));
			}
			if (this.serverCapabilities.prompts() == null) {
				return Mono.error(new McpError("Server must be configured with prompt capabilities"));
			}

			return Mono.defer(() -> {
				McpServerFeatures.AsyncPromptSpecification removed = this.prompts.remove(promptName);

				if (removed != null) {
					logger.debug("Removed prompt handler: {}", promptName);
					// Servers that declared the listChanged capability SHOULD send a
					// notification, when the list of available prompts changes
					if (this.serverCapabilities.prompts().listChanged()) {
						return this.notifyPromptsListChanged();
					}
					return Mono.empty();
				}
				return Mono.error(new McpError("Prompt with name '" + promptName + "' not found"));
			});
		}

		@Override
		public Mono<Void> notifyPromptsListChanged() {
			return this.mcpTransportProvider.notifyClients(McpSchema.METHOD_NOTIFICATION_PROMPTS_LIST_CHANGED, null);
		}

		private McpServerSession.RequestHandler<McpSchema.ListPromptsResult> promptsListRequestHandler() {
			return (exchange, params) -> {
				// TODO: Implement pagination
				// McpSchema.PaginatedRequest request = objectMapper.convertValue(params,
				// new TypeReference<McpSchema.PaginatedRequest>() {
				// });

				var promptList = this.prompts.values()
					.stream()
					.map(McpServerFeatures.AsyncPromptSpecification::prompt)
					.toList();

				return Mono.just(new McpSchema.ListPromptsResult(promptList, null));
			};
		}

		private McpServerSession.RequestHandler<McpSchema.GetPromptResult> promptsGetRequestHandler() {
			return (exchange, params) -> {
				McpSchema.GetPromptRequest promptRequest = objectMapper.convertValue(params,
						new TypeReference<McpSchema.GetPromptRequest>() {
						});

				// Implement prompt retrieval logic here
				McpServerFeatures.AsyncPromptSpecification specification = this.prompts.get(promptRequest.name());
				if (specification == null) {
					return Mono.error(new McpError("Prompt not found: " + promptRequest.name()));
				}

				return specification.promptHandler().apply(exchange, promptRequest);
			};
		}

		// ---------------------------------------
		// Logging Management
		// ---------------------------------------

		@Override
		public Mono<Void> loggingNotification(LoggingMessageNotification loggingMessageNotification) {

			if (loggingMessageNotification == null) {
				return Mono.error(new McpError("Logging message must not be null"));
			}

			Map<String, Object> params = this.objectMapper.convertValue(loggingMessageNotification,
					new TypeReference<Map<String, Object>>() {
					});

			if (loggingMessageNotification.level().level() < minLoggingLevel.level()) {
				return Mono.empty();
			}

			return this.mcpTransportProvider.notifyClients(McpSchema.METHOD_NOTIFICATION_MESSAGE, params);
		}

		private McpServerSession.RequestHandler<Void> setLoggerRequestHandler() {
			return (exchange, params) -> {
				this.minLoggingLevel = objectMapper.convertValue(params, new TypeReference<LoggingLevel>() {
				});

				return Mono.empty();
			};
		}

		// ---------------------------------------
		// Sampling
		// ---------------------------------------

		@Override
		void setProtocolVersions(List<String> protocolVersions) {
			this.protocolVersions = protocolVersions;
		}

		@Override
		public Mono<Void> registerUserSecretary(String userId, String secretaryName) {
			if (userId == null || secretaryName == null) {
				return Mono.error(new McpError("User ID and Secretary name must not be null"));
			}
			
			return userSecretaryRegistry.registerUserSecretary(userId, secretaryName)
				.doOnSuccess(v -> logger.info("Registered user [{}] with secretary [{}]", userId, secretaryName));
		}

		@Override
		public Mono<Void> unregisterUser(String userId) {
			if (userId == null) {
				return Mono.error(new McpError("User ID must not be null"));
			}
			
			return userSecretaryRegistry.unregisterAllUserSecretaries(userId)
				.doOnSuccess(v -> logger.info("Unregistered all secretaries for user [{}]", userId));
		}

		@Override
		public Mono<String> getSecretaryForUser(String userId) {
			return userSecretaryRegistry.getPrimarySecretaryForUser(userId);
		}
		
		@Override
		public Flux<String> getSecretariesForUser(String userId) {
			if (userId == null) {
				return Flux.error(new McpError("User ID must not be null"));
			}
			return userSecretaryRegistry.getSecretariesForUser(userId);
		}

		@Override
		public Mono<Void> unregisterUserSecretary(String userId, String secretaryName) {
			if (userId == null || secretaryName == null) {
				return Mono.error(new McpError("User ID and Secretary name must not be null"));
			}
			
			return userSecretaryRegistry.unregisterUserSecretary(userId, secretaryName)
				.doOnSuccess(v -> logger.info("Unregistered user [{}] from secretary [{}]", userId, secretaryName));
		}

		@Override
		public Mono<Map<String, List<String>>> getAllUserSecretaryMappings() {
			return userSecretaryRegistry.getAllUserSecretaryMappings();
		}

	}

}
