package com.hexagram2021.embodimentlib.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexagram2021.embodimentlib.api.AgentHostSide;
import com.hexagram2021.embodimentlib.api.AgentProfile;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 双端配置单测：用 night-config 直接解析示例 TOML 并灌入 spec
 * （绕过 FML 加载，等价于验证真实文件解析路径），再断言路由解析行为。
 * <p>
 * 26.1 约束：routing 为 {@code List<String>}（"agentType=profileName"），
 * profiles 为 {@code List<String>}（每个元素一个 JSON 对象字符串，含 name 字段）。
 */
class EmbodimentConfigTest {

	private static HostConfig loadSample(String toml) throws Exception {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		HostConfig cfg = HostConfig.create(builder);
		ModConfigSpec spec = builder.build();
		CommentedConfig parsed = new TomlParser().parse(toml);
		// 直接注入 loadedConfig 字段（acceptConfig 内部会调 save()，需要真实 ModConfig 才能工作）
		Field loadedConfigField = ModConfigSpec.class.getDeclaredField("loadedConfig");
		loadedConfigField.setAccessible(true);
		loadedConfigField.set(spec, fakeLoadedConfig(parsed));
		return cfg;
	}

	/**
	 * IConfigSpec.ILoadedConfig 是 sealed 接口（唯一实现为包私有 record LoadedConfig），
	 * 无法直接实现或代理；这里用反射构造 LoadedConfig 实例（path/modConfig 传 null，
	 * 本测试路径只会读取 config()）。
	 */
	private static IConfigSpec.ILoadedConfig fakeLoadedConfig(CommentedConfig config) throws Exception {
		Class<?> loadedConfigClass = Class.forName("net.neoforged.fml.config.LoadedConfig");
		Constructor<?> ctor = loadedConfigClass.getDeclaredConstructor(
			CommentedConfig.class, Path.class, ModConfig.class);
		ctor.setAccessible(true);
		return (IConfigSpec.ILoadedConfig) ctor.newInstance(config, null, null);
	}

	private static final String QUEST_GIVER_JSON =
		"{\"name\":\"quest_giver\",\"protocol\":\"openai\",\"base_url\":\"https://api.openai.com/v1\",\"api_key\":\"\",\"model_name\":\"gpt-4o-mini\"}";

	private static final String SAMPLE_TOML = """
		routing = ["village_npc=quest_giver", "demo_agent=default"]

		profiles = ['%s']

		[default]
		protocol = "anthropic"
		base_url = "https://api.anthropic.com"
		api_key = "sk-ant-sample"
		model_name = "claude-sonnet-4-5"
		""".formatted(QUEST_GIVER_JSON);

	@Test
	void parsesSampleFile() throws Exception {
		HostConfig cfg = loadSample(SAMPLE_TOML);
		AgentProfile def = cfg.defaultProfile();
		assertTrue(def.isAnthropic());
		assertEquals("sk-ant-sample", def.apiKey());
	}

	@Test
	void routesToNamedProfile() throws Exception {
		HostConfig cfg = loadSample(SAMPLE_TOML);
		AgentProfile p = cfg.resolveProfile("village_npc");
		assertTrue(p.isOpenAI());
		assertEquals("gpt-4o-mini", p.modelName());
	}

	@Test
	void explicitDefaultRoutingFallsBackToDefault() throws Exception {
		HostConfig cfg = loadSample(SAMPLE_TOML);
		AgentProfile p = cfg.resolveProfile("demo_agent");
		assertTrue(p.isAnthropic());
		assertEquals("sk-ant-sample", p.apiKey());
	}

	@Test
	void unknownAgentTypeFallsBackToDefault() throws Exception {
		HostConfig cfg = loadSample(SAMPLE_TOML);
		AgentProfile p = cfg.resolveProfile("no_such_type");
		assertTrue(p.isAnthropic());
	}

	@Test
	void danglingRoutingFallsBackToDefaultWithoutThrowing() throws Exception {
		HostConfig cfg = loadSample("""
			routing = ["some_type=ghost_profile"]

			[default]
			protocol = "openai"
			base_url = "https://api.openai.com/v1"
			api_key = ""
			model_name = "gpt-4o-mini"
			""");
		AgentProfile p = cfg.resolveProfile("some_type");
		assertTrue(p.isOpenAI());
		assertEquals("gpt-4o-mini", p.modelName());
	}

	@Test
	void invalidProfileFallsBackToDefaultWithoutThrowing() throws Exception {
		HostConfig cfg = loadSample("""
			routing = ["some_type=bad"]

			profiles = ['{"name":"bad","protocol":"gemini","base_url":"","api_key":"","model_name":""}']

			[default]
			protocol = "openai"
			base_url = "https://api.openai.com/v1"
			api_key = ""
			model_name = "gpt-4o-mini"
			""");
		AgentProfile p = cfg.resolveProfile("some_type");
		assertTrue(p.isOpenAI());
		assertEquals("gpt-4o-mini", p.modelName());
	}

	@Test
	void defaultValuesWhenConfigEmpty() throws Exception {
		// 空配置：get() 应回退 spec 默认值
		HostConfig cfg = loadSample("# empty config");
		AgentProfile def = cfg.defaultProfile();
		assertTrue(def.isOpenAI());
		assertEquals("https://api.openai.com/v1", def.baseUrl());
		assertEquals("gpt-4o-mini", def.modelName());
		assertTrue(def.apiKey().isEmpty());
		assertTrue(cfg.resolveProfile("whatever").isOpenAI());
	}

	/**
	 * 防御性回退：{@code [default]} 段字段非法时，{@code defaultProfile()} 捕获
	 * {@link IllegalArgumentException} 并回退到内置默认 profile，不向调用方抛异常。
	 * <p>
	 * 正常路径下 ModConfigSpec 校验器已保证字段合法（见 AgentProfileConfig），
	 * 本用例通过校验器未覆盖的空白 base_url 触发解析期异常路径。
	 */
	@Test
	void illegalDefaultProfileFallsBackWithoutThrowing() throws Exception {
		HostConfig cfg = loadSample("""
			[default]
			protocol = "openai"
			base_url = " "
			api_key = ""
			model_name = "gpt-4o-mini"
			""");
		AgentProfile def = cfg.defaultProfile();
		assertTrue(def.isOpenAI());
		assertEquals("https://api.openai.com/v1", def.baseUrl());
		assertEquals("gpt-4o-mini", def.modelName());
	}

	@Test
	void serverAndClientAreIndependent() throws Exception {
		HostConfig server = loadSample(SAMPLE_TOML);
		HostConfig client = loadSample("# empty config");
		assertTrue(server.resolveProfile("village_npc").isOpenAI());
		assertTrue(client.resolveProfile("village_npc").isOpenAI()); // 客户端未配置 → 默认
		assertNotEquals(server.defaultProfile().apiKey(), client.defaultProfile().apiKey());
	}

	@Test
	void hostSidePaths() {
		Path configDir = Path.of("C:", "config");
		assertEquals(Path.of("C:", "config", "embodimentlib", "server.toml"), AgentHostSide.SERVER.configFile(configDir));
		assertEquals(Path.of("C:", "config", "embodimentlib", "client.toml"), AgentHostSide.CLIENT.configFile(configDir));
		assertEquals("server.toml", AgentHostSide.SERVER.configFileName());
		assertEquals("client.toml", AgentHostSide.CLIENT.configFileName());
	}

	/**
	 * 兼容土耳其语区域设置：默认区域为 tr-TR 时，{@code "CLIENT".toLowerCase()}
	 * 会产出 {@code "clıent"}（无点 i），必须使用 {@link java.util.Locale#ROOT} 规避。
	 */
	@Test
	void configFileNameIsLocaleIndependent() {
		java.util.Locale original = java.util.Locale.getDefault();
		try {
			java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
			assertEquals("server.toml", AgentHostSide.SERVER.configFileName());
			assertEquals("client.toml", AgentHostSide.CLIENT.configFileName());
			assertEquals(Path.of("C:", "config", "embodimentlib", "client.toml"),
				AgentHostSide.CLIENT.configFile(Path.of("C:", "config")));
		} finally {
			java.util.Locale.setDefault(original);
		}
	}

	@Test
	void malformedRoutingEntriesAreSkipped() {
		Map<String, String> routing = HostConfig.parseRouting(
			List.of("village_npc=quest_giver", "no-separator", "=empty-key", "empty-value=", "", "  "));
		assertEquals(Map.of("village_npc", "quest_giver"), routing);
	}

	@Test
	void parseProfileValidJson() {
		HostConfig.NamedProfile named = HostConfig.parseProfile(QUEST_GIVER_JSON);
		assertEquals("quest_giver", named.name());
		assertTrue(named.profile().isOpenAI());
		assertEquals("gpt-4o-mini", named.profile().modelName());
	}

	@Test
	void parseProfileMissingNameThrows() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
			() -> HostConfig.parseProfile("{\"protocol\":\"openai\",\"base_url\":\"https://api.openai.com/v1\"}"));
		assertTrue(ex.getMessage().contains("name"));
	}

	@Test
	void parseProfileNotJsonThrows() {
		assertThrows(IllegalArgumentException.class, () -> HostConfig.parseProfile("not json at all"));
		assertThrows(IllegalArgumentException.class, () -> HostConfig.parseProfile("[1,2,3]"));
		assertThrows(IllegalArgumentException.class, () -> HostConfig.parseProfile("   "));
	}

	@Test
	void parseProfileInvalidFieldsThrow() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
			() -> HostConfig.parseProfile(
				"{\"name\":\"bad\",\"protocol\":\"gemini\",\"base_url\":\"\",\"api_key\":\"\",\"model_name\":\"\"}"));
		assertTrue(ex.getMessage().contains("bad"));
	}
}
