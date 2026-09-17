package com.hexagram2021.embodimentlib.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentProfileTest {

	@Test
	void openAiProfile() {
		AgentProfile p = new AgentProfile("openai", "https://api.openai.com/v1", "sk-test", "gpt-4o-mini");
		assertTrue(p.isOpenAI());
		assertFalse(p.isAnthropic());
		assertEquals("gpt-4o-mini", p.modelName());
	}

	@Test
	void anthropicProfile() {
		AgentProfile p = new AgentProfile("anthropic", "https://api.anthropic.com", "", "claude-sonnet-4-5");
		assertTrue(p.isAnthropic());
		assertTrue(p.apiKey().isEmpty()); // 本地端点允许空 key
	}

	@Test
	void rejectsUnknownProtocol() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new AgentProfile("gemini", "https://example.com", "", "m")
		);
	}

	@Test
	void rejectsBlankBaseUrl() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new AgentProfile("openai", " ", "", "gpt-4o-mini")
		);
	}

	@Test
	void rejectsBlankModelName() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new AgentProfile("openai", "https://api.openai.com/v1", "", " ")
		);
	}

	@Test
	void rejectsNullProtocol() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new AgentProfile(null, "https://example.com", "", "m")
		);
	}
}
