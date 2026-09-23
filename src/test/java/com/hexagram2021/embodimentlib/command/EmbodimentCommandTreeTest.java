package com.hexagram2021.embodimentlib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.PermissionSetSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EmbodimentCommandTree} 的单测（PLAN WP-8 验收标准 5：别名命令树解析正确）。
 * <p>
 * <b>为什么能给命令树写单测</b>：{@link EmbodimentCommandTree#rootNode} 对命令源类型泛型化，
 * 于是本用例可以用一个哑 source 类型注册<b>同一棵树</b>，再让真实 Brigadier 去解析命令串。
 * 这比「断言几个字符串常量」强得多——别名少注册一个、子命令改名、参数名写错、
 * 两个执行器接反、权限谓词漏挂，都会被下面这些断言直接抓住。
 * <p>
 * 哑 source 实现 {@link PermissionSetSupplier} 并授予全部权限，因为 {@code @e} 这类选择器
 * 在解析期会检查 {@code Permissions.COMMANDS_ENTITY_SELECTORS}
 * （见 {@code EntitySelectorParser#allowSelectors}）。
 */
class EmbodimentCommandTreeTest {
	/** 只提供权限的哑命令源（Brigadier 解析期唯一会用到的东西）。 */
	private record FakeSource(PermissionSet permissions) implements PermissionSetSupplier {
	}

	private static final FakeSource SOURCE = new FakeSource(PermissionSet.ALL_PERMISSIONS);

	/** 记录两个分支各自被调用了多少次。 */
	private static final class Recording {
		private final AtomicInteger fallbackCalls = new AtomicInteger();
		private final AtomicInteger explicitCalls = new AtomicInteger();

		int fallbackCalls() {
			return this.fallbackCalls.get();
		}

		int explicitCalls() {
			return this.explicitCalls.get();
		}

		/** 用同一批构造器注册主命令与别名（与生产代码同构）。 */
		private CommandDispatcher<FakeSource> dispatcher(boolean permitted) {
			CommandDispatcher<FakeSource> dispatcher = new CommandDispatcher<>();
			for (String literal : List.of(EmbodimentCommandTree.ROOT_LITERAL, EmbodimentCommandTree.ALIAS_LITERAL)) {
				dispatcher.register(EmbodimentCommandTree.rootNode(literal,
					source -> permitted,
					context -> {
						this.fallbackCalls.incrementAndGet();
						return 1;
					},
					context -> {
						this.explicitCalls.incrementAndGet();
						return 1;
					}));
			}
			return dispatcher;
		}
	}

	private static CommandNode<FakeSource> inspectNode(CommandDispatcher<FakeSource> dispatcher, String root) {
		return dispatcher.getRoot().getChild(root).getChild(EmbodimentCommandTree.INSPECT_LITERAL);
	}

	private static List<String> parsedNodeNames(CommandDispatcher<FakeSource> dispatcher, String command) {
		ParseResults<FakeSource> parsed = dispatcher.parse(command, SOURCE);
		return parsed.getContext().getNodes().stream()
			.map(node -> node.getNode().getName())
			.toList();
	}

	@Test
	@DisplayName("验收⑤：主命令与别名都注册，且都有 inspect 子命令")
	void bothRootsAreRegisteredWithInspectChild() {
		CommandDispatcher<FakeSource> dispatcher = new Recording().dispatcher(true);

		assertNotNull(dispatcher.getRoot().getChild(EmbodimentCommandTree.ROOT_LITERAL), "缺少 /embodimentlib");
		assertNotNull(dispatcher.getRoot().getChild(EmbodimentCommandTree.ALIAS_LITERAL), "缺少别名 /emb");
		assertNotNull(inspectNode(dispatcher, EmbodimentCommandTree.ROOT_LITERAL), "主命令缺少 inspect");
		assertNotNull(inspectNode(dispatcher, EmbodimentCommandTree.ALIAS_LITERAL), "别名缺少 inspect");
		assertEquals(List.of(EmbodimentCommandTree.ROOT_LITERAL, EmbodimentCommandTree.ALIAS_LITERAL),
			dispatcher.getRoot().getChildren().stream().map(CommandNode::getName).toList());
	}

	@Test
	@DisplayName("验收⑤：inspect 的参数名/类型正确，根节点与参数节点各挂一个不同的执行器")
	void inspectNodeShape() {
		CommandDispatcher<FakeSource> dispatcher = new Recording().dispatcher(true);
		CommandNode<FakeSource> inspect = inspectNode(dispatcher, EmbodimentCommandTree.ALIAS_LITERAL);

		assertNotNull(inspect.getCommand(), "inspect 自身应挂「未给参数」的执行器");
		assertEquals(List.of(EmbodimentCommandTree.ENTITY_ARGUMENT),
			inspect.getChildren().stream().map(CommandNode::getName).toList());

		CommandNode<FakeSource> entity = inspect.getChild(EmbodimentCommandTree.ENTITY_ARGUMENT);
		assertNotNull(entity.getCommand(), "带参数的节点应挂「显式目标」的执行器");
		assertNotSame(inspect.getCommand(), entity.getCommand(), "两个分支必须是不同的执行器");
		// 参数类型必须是实体选择器：名字 / UUID / @e 三种写法都由它承担解析。
		assertTrue(entity instanceof ArgumentCommandNode<FakeSource, ?>);
		assertEquals(EntityArgument.class, ((ArgumentCommandNode<FakeSource, ?>) entity).getType().getClass());
	}

	@Test
	@DisplayName("验收⑤：/emb inspect 解析到「未给参数」分支并执行它")
	void aliasWithoutArgumentParsesToFallback() throws CommandSyntaxException {
		Recording recording = new Recording();
		CommandDispatcher<FakeSource> dispatcher = recording.dispatcher(true);

		assertEquals(List.of(EmbodimentCommandTree.ALIAS_LITERAL, EmbodimentCommandTree.INSPECT_LITERAL),
			parsedNodeNames(dispatcher, "emb inspect"));

		assertEquals(1, dispatcher.execute("emb inspect", SOURCE));
		assertEquals(1, recording.fallbackCalls());
		assertEquals(0, recording.explicitCalls());
	}

	@Test
	@DisplayName("验收⑤：/embodimentlib inspect <名字> 解析到「显式目标」分支并执行它")
	void mainCommandWithArgumentParsesToExplicit() throws CommandSyntaxException {
		Recording recording = new Recording();
		CommandDispatcher<FakeSource> dispatcher = recording.dispatcher(true);

		assertEquals(List.of(EmbodimentCommandTree.ROOT_LITERAL, EmbodimentCommandTree.INSPECT_LITERAL,
			EmbodimentCommandTree.ENTITY_ARGUMENT), parsedNodeNames(dispatcher, "embodimentlib inspect Steve"));

		assertEquals(1, dispatcher.execute("embodimentlib inspect Steve", SOURCE));
		assertEquals(1, recording.explicitCalls());
		assertEquals(0, recording.fallbackCalls());
	}

	@Test
	@DisplayName("验收⑤：选择器（@e[...]）与 UUID 形态都被解析（EntityArgument 原生支持三种写法）")
	void selectorAndUuidFormsParse() {
		CommandDispatcher<FakeSource> dispatcher = new Recording().dispatcher(true);
		String argument = EmbodimentCommandTree.ENTITY_ARGUMENT;

		assertTrue(parsedNodeNames(dispatcher, "emb inspect @e[type=minecraft:pig,limit=1]").contains(argument));
		assertTrue(parsedNodeNames(dispatcher, "emb inspect dd12be42-52a9-4a91-a8a1-11c01849e498").contains(argument));
	}

	@Test
	@DisplayName("验收③：权限谓词不满足时命令在解析期就不可达（非操作员拿不到这个命令）")
	void permissionRequirementHidesCommand() {
		CommandDispatcher<FakeSource> dispatcher = new Recording().dispatcher(false);

		assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("emb inspect", SOURCE),
			"无权限时执行应失败");
		assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("embodimentlib inspect Steve", SOURCE),
			"无权限时带参数执行也应失败");
	}

	@Test
	@DisplayName("常量：命令字面量与用法提示与 PLAN 的约定一致")
	void literalsMatchPlan() {
		assertEquals("embodimentlib", EmbodimentCommandTree.ROOT_LITERAL);
		assertEquals("emb", EmbodimentCommandTree.ALIAS_LITERAL);
		assertEquals("inspect", EmbodimentCommandTree.INSPECT_LITERAL);
		assertEquals("entity", EmbodimentCommandTree.ENTITY_ARGUMENT);
		assertEquals("/embodimentlib inspect [entity] (alias /emb inspect)", EmbodimentCommandTree.USAGE_HINT);
	}
}
