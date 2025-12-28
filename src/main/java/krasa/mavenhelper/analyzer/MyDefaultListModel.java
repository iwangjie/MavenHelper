package krasa.mavenhelper.analyzer;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class MyDefaultListModel extends AbstractListModel<MyListNode> implements Iterable<MyListNode> {
	public static final Comparator<MyListNode> DEEP_SIZE = new Comparator<MyListNode>() {
		@Override
		public int compare(MyListNode t0, MyListNode t1) {
			return Long.compare(t1.getTotalSize(), t0.getTotalSize());
		}
	};
	public static final Comparator<MyListNode> GROUP_ID = new Comparator<MyListNode>() {
		@Override
		public int compare(MyListNode t0, MyListNode t1) {
			int i = t0.getGroupId().compareTo(t1.getGroupId());
			if (i == 0) {
				i = t0.getArtifactId().compareTo(t1.getArtifactId());
			}
			return i;
		}
	};
	public static final Comparator<MyListNode> ARTIFACT_ID = new Comparator<MyListNode>() {
		@Override
		public int compare(MyListNode t0, MyListNode t1) {
			return t0.getArtifactId().compareTo(t1.getArtifactId());
		}
	};
	public static final Comparator<MyListNode> SHALLOW_SIZE = new Comparator<MyListNode>() {
		@Override
		public int compare(MyListNode t0, MyListNode t1) {
			return Long.compare(t1.getSize(), t0.getSize());
		}
	};

	private final List<MyListNode> items = new ArrayList<>();

	@Override
	public int getSize() {
		return items.size();
	}

	@Override
	public MyListNode getElementAt(int index) {
		return items.get(index);
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public void clear() {
		int oldSize = items.size();
		if (oldSize == 0) {
			return;
		}
		items.clear();
		fireIntervalRemoved(this, 0, oldSize - 1);
	}

	public void setItems(List<MyListNode> newItems) {
		int oldSize = items.size();
		items.clear();
		items.addAll(newItems);

		int newSize = items.size();
		if (oldSize == 0 && newSize == 0) {
			return;
		}
		fireContentsChanged(this, 0, Math.max(oldSize, newSize) - 1);
	}

	public void sort(Comparator<MyListNode> comparator) {
		if (items.size() < 2) {
			return;
		}
		items.sort(comparator);
		fireContentsChanged(this, 0, items.size() - 1);
	}

	@Override
	public Iterator<MyListNode> iterator() {
		return items.iterator();
	}
}
