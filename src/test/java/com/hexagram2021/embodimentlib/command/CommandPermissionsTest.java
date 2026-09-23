package com.hexagram2021.embodimentlib.command;

import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.server.permissions.Permissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CommandPermissions} 的单测（PLAN WP-8 验收标准 3：非操作员被拒）。
 * <p>
 * {@link PermissionSet} 是函数式接口，而 {@link LevelBasedPermissionSet} 提供了「某档 op 等级」
 * 的真实权限集常量，因此这里能直接验证<b>等级语义</b>（恰好二级放行、三级及以上放行、
 * 一级及以下拒绝），无需服务器与玩家——这正是不把权限判定写成散落 lambda 的价值：
 * 拒绝分支有专门的用例，且阈值被钉死在「gamemaster」这一档上。
 */
class CommandPermissionsTest {
	@Test
	@DisplayName("验收③：所需权限 = 操作员二级（gamemaster）")
	void requiredPermissionIsGamemaster() {
		assertEquals(Permissions.COMMANDS_GAMEMASTER, CommandPermissions.REQUIRED);
	}

	@Test
	@DisplayName("验收③：恰好二级（gamemaster）放行")
	void exactlyLevelTwoIsAllowed() {
		assertTrue(CommandPermissions.canInspect(LevelBasedPermissionSet.GAMEMASTER));
	}

	@Test
	@DisplayName("验收③：更高档（admin/owner）放行——高等级包含低等级权限")
	void higherLevelsAreAllowed() {
		assertTrue(CommandPermissions.canInspect(LevelBasedPermissionSet.ADMIN));
		assertTrue(CommandPermissions.canInspect(LevelBasedPermissionSet.OWNER));
	}

	@Test
	@DisplayName("验收③：一级及以下（all/moderator）被拒——阈值不能放宽")
	@SuppressWarnings({"deprecation", "java:S1874"})
	void lowerLevelsAreRejected() {
		assertFalse(CommandPermissions.canInspect(LevelBasedPermissionSet.MODERATOR));
		assertFalse(CommandPermissions.canInspect(LevelBasedPermissionSet.ALL));
	}

	@Test
	@DisplayName("验收③：空权限与全权限两端行为正确")
	void emptyAndFullSets() {
		assertFalse(CommandPermissions.canInspect(PermissionSet.NO_PERMISSIONS));
		assertTrue(CommandPermissions.canInspect(PermissionSet.ALL_PERMISSIONS));
	}

	@Test
	@DisplayName("验收③：并集语义——无权限集合并入更高档后放行（命令源常由多个来源拼权限）")
	void unionOfPermissionSets() {
		PermissionSet combined = PermissionSet.NO_PERMISSIONS.union(LevelBasedPermissionSet.ADMIN);
		assertTrue(CommandPermissions.canInspect(combined));
		assertFalse(CommandPermissions.canInspect(
			PermissionSet.NO_PERMISSIONS.union(LevelBasedPermissionSet.MODERATOR)));
	}

	@Test
	@DisplayName("拒绝文案固定且不含任何敏感信息")
	void deniedMessageIsFixedText() {
		assertEquals("Requires operator permission level 2 (gamemaster) to inspect an agent.",
			CommandPermissions.DENIED_MESSAGE);
		assertEquals(CommandPermissions.DENIED_MESSAGE, CommandPermissions.deniedMessage().getString());
	}
}
