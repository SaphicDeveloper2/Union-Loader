package dev.union.impl.client.keymapping;

import java.util.Comparator;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * Orders {@link KeyMapping.Category} entries so that vanilla categories appear first (in
 * their declaration order — assumes a stable sort) and modded categories appear after,
 * sorted by namespace + path.
 *
 * <p>Ported from {@code union-key-mapping-api-v1}. No behavioural change.
 */
public final class CategoryComparator implements Comparator<KeyMapping.Category> {
	public static final CategoryComparator INSTANCE = new CategoryComparator();

	private CategoryComparator() { }

	@Override
	public int compare(KeyMapping.Category o1, KeyMapping.Category o2) {
		boolean o1Vanilla = o1.id().getNamespace().equals(Identifier.DEFAULT_NAMESPACE);
		boolean o2Vanilla = o2.id().getNamespace().equals(Identifier.DEFAULT_NAMESPACE);

		if (o1Vanilla && o2Vanilla) {
			return 0;
		}

		if (o1Vanilla) {
			return -1;
		} else if (o2Vanilla) {
			return 1;
		}

		int c = o1.id().getNamespace().compareTo(o2.id().getNamespace());

		if (c != 0) {
			return c;
		}

		return o1.id().getPath().compareTo(o2.id().getPath());
	}
}
