package io.hummer.util.jsf;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class serves as a generic backing bean class 
 * for <h:dataTable> JSF UI components.
 * 
 * Columns 1-20 of each row can be accessed via .col1 to .col20
 * 
 * @author Waldemar Hummer
 */
public class DataTableBean {

	public static class DataTableBeanRow {
		private final List<Object> values = new LinkedList<Object>();
		private int rowNumber = 0;

		public DataTableBeanRow() {}
		public DataTableBeanRow(Object ... rowValues) {
			values.addAll(Arrays.asList(rowValues));
		}

		public List<Object> getValues() {
			return values;
		}
		public Object getCol1() { return getCol(0); }
		public Object getCol2() { return getCol(1); }
		public Object getCol3() { return getCol(2); }
		public Object getCol4() { return getCol(3); }
		public Object getCol5() { return getCol(4); }
		public Object getCol6() { return getCol(5); }
		public Object getCol7() { return getCol(6); }
		public Object getCol8() { return getCol(7); }
		public Object getCol9() { return getCol(8); }
		public Object getCol10() { return getCol(9); }
		public Object getCol11() { return getCol(10); }
		public Object getCol12() { return getCol(11); }
		public Object getCol13() { return getCol(12); }
		public Object getCol14() { return getCol(13); }
		public Object getCol15() { return getCol(14); }
		public Object getCol16() { return getCol(15); }
		public Object getCol17() { return getCol(16); }
		public Object getCol18() { return getCol(17); }
		public Object getCol19() { return getCol(18); }
		public Object getCol20() { return getCol(19); }

		public void setCol1(Object o) { setCol(0, o); }
		public void setCol2(Object o) { setCol(1, o); }
		public void setCol3(Object o) { setCol(2, o); }
		public void setCol4(Object o) { setCol(3, o); }
		public void setCol5(Object o) { setCol(4, o); }
		public void setCol6(Object o) { setCol(5, o); }
		public void setCol7(Object o) { setCol(6, o); }
		public void setCol8(Object o) { setCol(7, o); }
		public void setCol9(Object o) { setCol(8, o); }
		public void setCol10(Object o) { setCol(9, o); }
		public void setCol11(Object o) { setCol(10, o); }
		public void setCol12(Object o) { setCol(11, o); }
		public void setCol13(Object o) { setCol(12, o); }
		public void setCol14(Object o) { setCol(13, o); }
		public void setCol15(Object o) { setCol(14, o); }
		public void setCol16(Object o) { setCol(15, o); }
		public void setCol17(Object o) { setCol(16, o); }
		public void setCol18(Object o) { setCol(17, o); }
		public void setCol19(Object o) { setCol(18, o); }
		public void setCol20(Object o) { setCol(19, o); }

		private Object getCol(int i) {
			return values.size() > i ? values.get(i) : null;
		}
		private void setCol(int i, Object o) {
			if(values.size() > i) 
				values.set(i, o);
			else if(values.size() == i) 
				values.add(o);
		}
		public int getRowNumber() {
			return rowNumber;
		}
		public void setRowNumber(int rowNumber) {
			this.rowNumber = rowNumber;
		}
		
	}

	private final List<DataTableBeanRow> rows = new LinkedList<DataTableBean.DataTableBeanRow>();

	public List<DataTableBeanRow> getRows() {
		return rows;
	}
	public void addRow(DataTableBeanRow row) {
		row.setRowNumber(rows.size());
		rows.add(row);
	}

}
