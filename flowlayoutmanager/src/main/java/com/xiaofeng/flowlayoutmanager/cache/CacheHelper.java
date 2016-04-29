package com.xiaofeng.flowlayoutmanager.cache;

import android.graphics.Point;
import android.util.SparseArray;

import com.xiaofeng.flowlayoutmanager.FlowLayoutOptions;

/**
 * Created by xhan on 4/27/16.
 */
public class CacheHelper {
	public static final int NOT_FOUND = -1;
	final int itemPerLine;
	int contentAreaWidth;
	SparseArray<Point> sizeMap;
	SparseArray<Line> lineMap;

	public CacheHelper(FlowLayoutOptions layoutOptions, int contentAreaWidth) {
		this.itemPerLine = layoutOptions.itemsPerLine;
		this.contentAreaWidth = contentAreaWidth;
		sizeMap = new SparseArray<>();
		lineMap = new SparseArray<>();
	}

	public void add(int startIndex, Point... sizes) {
		if (!valid()) {
			return;
		}
		invalidateLineMapAfter(startIndex);
		makeSpace(startIndex, sizes.length);
		int index = startIndex;
		for (Point size : sizes) {
			sizeMap.put(index ++, size);
		}
	}

	public void add(int startIndex, int count) {
		if (!valid()) {
			return;
		}
		invalidateLineMapAfter(startIndex);
		makeSpace(startIndex, count);
	}

	public void invalidSizes(int index, int count) {
		if (!valid()) {
			return;
		}
		invalidateLineMapAfter(index);
		int actualCount = actualCount(index, count);
		for (int i = 0; i < actualCount; i ++) {
			sizeMap.remove(index + i);
		}
	}

	public void remove(int index, int count) {
		if (!valid()) {
			return;
		}
		invalidateLineMapAfter(index);
		int actualCount = actualCount(index, count);
		for (int i = 0; i < actualCount; i ++) {
			sizeMap.remove(index + i);
		}

		// move everything behind to fill the hole.
		for (int i = index + actualCount; i < sizeMap.size() + actualCount; i ++) {
			Point tmp = sizeMap.get(i);
			sizeMap.remove(i);
			sizeMap.put(i - actualCount, tmp);
		}
	}

	public void setItem(int index, Point newSize) {
		if (!valid()) {
			return;
		}
		if (sizeMap.get(index, null) != null) {
			Point cachedPoint = sizeMap.get(index);
			if (!cachedPoint.equals(newSize)) {
				invalidateLineMapAfter(index);
				sizeMap.put(index, newSize);
			}
		} else {
			invalidateLineMapAfter(index);
			sizeMap.put(index, newSize);
		}
	}

	/**
	 * Move items from one place to another. no check on parameter as invoker will make sure it is correct
	 */
	public void move(int from, int to, int count) {
		if (!valid()) {
			return;
		}
		invalidateLineMapAfter(Math.min(from, to));
		Point[] itemsToMove = new Point[count];
		for (int i = from; i < from + count; i ++) {
			itemsToMove[i - from] = sizeMap.get(i);
		}
		boolean movingForward = from - to > 0;
		int itemsToShift = Math.abs(from - to);

		if (!movingForward) {
			itemsToShift -= count;
		}
		int shiftIndex = movingForward ? from - 1 : from + count;
		int shiftIndexStep = movingForward ? -1 : 1;

		int shifted = 0;
		while (shifted < itemsToShift) {
			sizeMap.put(shiftIndex - (shiftIndexStep) * count, sizeMap.get(shiftIndex));
			shiftIndex += shiftIndexStep;
			shifted ++;
		}

		int setIndex = to;
		if (!movingForward) {
			setIndex = from + itemsToShift;
		}
		for (Point item : itemsToMove) {
			sizeMap.put(setIndex++, item);
		}
	}

	public int[] getLineMap() {
		if (!valid()) {
			return new int[0];
		}
		refreshLineMap();
		int[] lineCounts = new int[this.lineMap.size()];
		for (int i = 0; i < this.lineMap.size(); i ++) {
			lineCounts[i] = this.lineMap.get(i).itemCount;
		}
		return lineCounts;
	}

	public int itemLineIndex(int itemIndex) {
		if (!valid()) {
			return NOT_FOUND;
		}
		refreshLineMap();
		int itemCount = 0;
		for (int i = 0; i < lineMap.size(); i ++) {
			itemCount += lineMap.get(i).itemCount;
			if (itemCount >= itemIndex + 1) {
				return i;
			}
		}
		return NOT_FOUND;
	}

	public Line containingLine(int itemIndex) {
		if (!valid()) {
			return null;
		}
		refreshLineMap();
		return getLine(itemLineIndex(itemIndex));
	}

	public int firstItemIndex(int lineIndex) {
		if (!valid()) {
			return NOT_FOUND;
		}
		refreshLineMap();
		int itemCount = 0;
		for (int i = 0; i < lineIndex; i ++) {
			itemCount += lineMap.get(i).itemCount;
		}
		return itemCount;
	}

	public Line getLine(int lineIndex) {
		if (!valid()) {
			return null;
		}
		refreshLineMap();
		return lineMap.get(lineIndex, null);
	}

	public boolean hasPreviousLineCached(int itemIndex) {
		if (!valid()) {
			return false;
		}
		refreshLineMap();
		int lineIndex = itemLineIndex(itemIndex);
		if (lineIndex == NOT_FOUND) {
			return false;
		}

		if (lineIndex > 0) {
			return true;
		}
		return false;
	}

	public boolean hasNextLineCached(int itemIndex) {
		if (!valid()) {
			return false;
		}
		refreshLineMap();
		int lineIndex = itemLineIndex(itemIndex);
		if (lineIndex == NOT_FOUND) {
			return false;
		}
		return !lineMap.get(lineIndex + 1, Line.EMPTY_LINE).equals(Line.EMPTY_LINE);
	}

	public void clear() {
		sizeMap.clear();
		lineMap.clear();
	}

	public String dumpCache() {
		refreshLineMap();
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("area width = " + contentAreaWidth).append("\n");
		stringBuilder.append("cached items = " + sizeMap.size()).append("\n");
		for (int i = 0; i < sizeMap.size(); i ++) {
			stringBuilder.append("cached item (" + i + ") = " + sizeMap.get(i)).append("\n");
		}

		stringBuilder.append("\nline map\n");
		for (int i = 0; i < lineMap.size(); i ++) {
			stringBuilder.append(lineMap.get(i)).append("\n");
		}
		return stringBuilder.toString();

	}
	//===================== Helper methods ========================

	/**
	 * Move item after startIndex to make {count} space(s)
	 */
	private void makeSpace(int startIndex, int count) {
		for (int i = sizeMap.size() - 1; i >= startIndex; i --) {
			sizeMap.put(i + count, sizeMap.get(i));
		}
		for (int i = startIndex; i < startIndex + count; i ++) {
			sizeMap.remove(i);
		}
	}

	/**
	 * Rebuild line map. and should stop if there is a hole (like item changed or item inserted but not measured)
	 */
	private void refreshLineMap() {
		if (!valid()) {
			return;
		}
		int index = refreshLineMapStartIndex();
		Point cachedSize = sizeMap.get(index, null);
		int lineWidth = 0;
		int lineIndex = lineMap.size();
		int lineItemCount = 0;
		Line currentLine = new Line();

		while (cachedSize != null) {
			lineWidth += cachedSize.x;
			lineItemCount ++;
			if (lineWidth <= contentAreaWidth) {
				if (itemPerLine > 0) { // have item per line limit
					if (lineItemCount > itemPerLine) { // exceed item per line limit
						lineMap.put(lineIndex, currentLine);

						// put this item to next line
						currentLine = new Line();
						addToLine(currentLine, cachedSize, index);
						lineIndex ++;
						lineWidth = cachedSize.x;
						lineItemCount = 1;
					} else {
						addToLine(currentLine, cachedSize, index);
					}
				} else {
					addToLine(currentLine, cachedSize, index);
				}
			} else { // too wide to add this item, put line item count to index and put this one to new line
				lineMap.put(lineIndex, currentLine);
				currentLine = new Line();
				addToLine(currentLine, cachedSize, index);
				lineIndex ++;
				lineWidth = cachedSize.x;
				lineItemCount = 1;

			}
			index ++;
			cachedSize = sizeMap.get(index, null);
		}

		if (currentLine.itemCount > 0) {
			lineMap.append(lineIndex, currentLine);
		}
	}

	/**
	 * Add view info to line
	 */
	private void addToLine(Line line, Point item, int index) {
		line.itemCount ++;
		line.totalWidth += item.x;
		line.maxHeight = item.y > line.maxHeight ? item.y : line.maxHeight;
		if (item.y == line.maxHeight) {
			line.maxHeightIndex = index;
		}
	}

	/**
	 * return actual count from index to expected count or end of sizeMap
	 */
	private int actualCount(int index, int count) {
		return index + count > sizeMap.size() ? sizeMap.size() - index : count;
	}

	/**
	 * Invalidate line map that contains item and all lines after
	 * @param itemIndex
	 */
	private void invalidateLineMapAfter(int itemIndex) {
		int itemLineIndex = itemLineIndex(itemIndex);
		Line line = lineMap.get(itemLineIndex, null);
		if (line == null && lineMap.size() > 0) {
			lineMap.remove(lineMap.size() - 1);
		}
		while (line != null) {
			lineMap.remove(itemLineIndex);
			itemLineIndex ++;
			line = lineMap.get(itemLineIndex, null);
		}
	}

	private int refreshLineMapStartIndex() {
		int itemCount = 0;
		for (int i = 0; i < lineMap.size(); i ++) {
			itemCount += lineMap.get(i).itemCount;
		}
		if (itemCount >= sizeMap.size()) {
			return NOT_FOUND;
		}
		return itemCount;
	}

	public void contentAreaWidth(int width) {
		contentAreaWidth = width;
		lineMap.clear();
		refreshLineMap();
	}

	public boolean valid() {
		return contentAreaWidth > 0;
	}
}