/*
 * Copyright (c) 1998-2017 by Richard A. Wilkes. All rights reserved.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, version 2.0. If a copy of the MPL was not distributed with
 * this file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, version 2.0.
 */

package com.trollworks.gcs.equipment;

import com.trollworks.gcs.character.GURPSCharacter;
import com.trollworks.gcs.common.DataFile;
import com.trollworks.gcs.common.ListFile;
import com.trollworks.gcs.library.LibraryFile;
import com.trollworks.gcs.menu.edit.Incrementable;
import com.trollworks.gcs.menu.edit.TechLevelIncrementable;
import com.trollworks.gcs.template.Template;
import com.trollworks.gcs.widgets.outline.ListOutline;
import com.trollworks.gcs.widgets.outline.ListRow;
import com.trollworks.gcs.widgets.outline.MultipleRowUndo;
import com.trollworks.gcs.widgets.outline.RowPostProcessor;
import com.trollworks.gcs.widgets.outline.RowUndo;
import com.trollworks.toolkit.annotation.Localize;
import com.trollworks.toolkit.collections.FilteredIterator;
import com.trollworks.toolkit.ui.widget.outline.OutlineModel;
import com.trollworks.toolkit.ui.widget.outline.Row;
import com.trollworks.toolkit.utility.Localization;
import com.trollworks.toolkit.utility.text.Numbers;

import java.awt.EventQueue;
import java.awt.dnd.DropTargetDragEvent;
import java.util.ArrayList;
import java.util.List;

/** An outline specifically for equipment. */
public class EquipmentOutline extends ListOutline implements Incrementable, TechLevelIncrementable {
    @Localize("Increment Quantity")
    @Localize(locale = "de", value = "Anzahl erhöhen")
    @Localize(locale = "ru", value = "Увеличить количество")
    @Localize(locale = "es", value = "Aumentar cantidad")
    private static String INCREMENT;
    @Localize("Decrement Quantity")
    @Localize(locale = "de", value = "Anzahl verringen")
    @Localize(locale = "ru", value = "Уменьшить количество")
    @Localize(locale = "es", value = "Disminuir cantidad")
    private static String DECREMENT;

    static {
        Localization.initialize();
    }

    private static OutlineModel extractModel(DataFile dataFile) {
        if (dataFile instanceof GURPSCharacter) {
            return ((GURPSCharacter) dataFile).getEquipmentRoot();
        }
        if (dataFile instanceof Template) {
            return ((Template) dataFile).getEquipmentModel();
        }
        if (dataFile instanceof LibraryFile) {
            return ((LibraryFile) dataFile).getEquipmentList().getModel();
        }
        return ((ListFile) dataFile).getModel();
    }

    /**
     * Create a new equipment outline.
     *
     * @param dataFile The owning data file.
     */
    public EquipmentOutline(DataFile dataFile) {
        this(dataFile, extractModel(dataFile));
    }

    /**
     * Create a new equipment outline.
     *
     * @param dataFile The owning data file.
     * @param model The {@link OutlineModel} to use.
     */
    public EquipmentOutline(DataFile dataFile, OutlineModel model) {
        super(dataFile, model, Equipment.ID_LIST_CHANGED);
        EquipmentColumn.addColumns(this, dataFile);
    }

    @Override
    public String getDecrementTitle() {
        return DECREMENT;
    }

    @Override
    public String getIncrementTitle() {
        return INCREMENT;
    }

    @Override
    public boolean canDecrement() {
        return (mDataFile instanceof GURPSCharacter || mDataFile instanceof Template) && selectionHasLeafRows(true);
    }

    @Override
    public boolean canIncrement() {
        return (mDataFile instanceof GURPSCharacter || mDataFile instanceof Template) && selectionHasLeafRows(false);
    }

    private boolean selectionHasLeafRows(boolean requireQtyAboveZero) {
        for (Equipment equipment : new FilteredIterator<>(getModel().getSelectionAsList(), Equipment.class)) {
            if (!equipment.canHaveChildren() && (!requireQtyAboveZero || equipment.getQuantity() > 0)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    @Override
    public void decrement() {
        List<RowUndo> undos = new ArrayList<>();
        for (Equipment equipment : new FilteredIterator<>(getModel().getSelectionAsList(), Equipment.class)) {
            if (!equipment.canHaveChildren()) {
                int qty = equipment.getQuantity();
                if (qty > 0) {
                    RowUndo undo = new RowUndo(equipment);
                    equipment.setQuantity(qty - 1);
                    if (undo.finish()) {
                        undos.add(undo);
                    }
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void increment() {
        List<RowUndo> undos = new ArrayList<>();
        for (Equipment equipment : new FilteredIterator<>(getModel().getSelectionAsList(), Equipment.class)) {
            if (!equipment.canHaveChildren()) {
                RowUndo undo = new RowUndo(equipment);
                equipment.setQuantity(equipment.getQuantity() + 1);
                if (undo.finish()) {
                    undos.add(undo);
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @Override
    public boolean canIncrementTechLevel() {
        return checkTechLevel(0);
    }

    @Override
    public boolean canDecrementTechLevel() {
        return checkTechLevel(1);
    }

    private boolean checkTechLevel(int minLevel) {
        for (Equipment equipment : new FilteredIterator<>(getModel().getSelectionAsList(), Equipment.class)) {
            if (getCurrentTechLevel(equipment) >= minLevel) {
                return true;
            }
        }
        return false;
    }

    private static int getCurrentTechLevel(Equipment equipment) {
        String tl = equipment.getTechLevel();
        if (tl != null) {
            tl = tl.trim();
            if (!tl.isEmpty()) {
                for (int i = tl.length(); --i >= 0;) {
                    if (!Character.isDigit(tl.charAt(i))) {
                        return -1;
                    }
                }
                return Numbers.extractInteger(tl, -1, 0, Integer.MAX_VALUE, false);
            }
        }
        return -1;
    }

    @SuppressWarnings("unused")
    @Override
    public void incrementTechLevel() {
        List<RowUndo> undos = new ArrayList<>();
        for (Equipment equipment : new FilteredIterator<>(getModel().getSelectionAsList(), Equipment.class)) {
            int tl = getCurrentTechLevel(equipment);
            if (tl >= 0) {
                RowUndo undo = new RowUndo(equipment);
                equipment.setTechLevel(Integer.toString(tl + 1));
                if (undo.finish()) {
                    undos.add(undo);
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @SuppressWarnings("unused")
    @Override
    public void decrementTechLevel() {
        List<RowUndo> undos = new ArrayList<>();
        for (Equipment equipment : new FilteredIterator<>(getModel().getSelectionAsList(), Equipment.class)) {
            if (!equipment.canHaveChildren()) {
                int tl = getCurrentTechLevel(equipment);
                if (tl >= 1) {
                    RowUndo undo = new RowUndo(equipment);
                    equipment.setTechLevel(Integer.toString(tl - 1));
                    if (undo.finish()) {
                        undos.add(undo);
                    }
                }
            }
        }
        if (!undos.isEmpty()) {
            repaintSelection();
            new MultipleRowUndo(undos);
        }
    }

    @Override
    protected boolean isRowDragAcceptable(DropTargetDragEvent dtde, Row[] rows) {
        return !getModel().isLocked() && rows.length > 0 && rows[0] instanceof Equipment;
    }

    @Override
    public void convertDragRowsToSelf(List<Row> list) {
        OutlineModel model = getModel();
        Row[] rows = model.getDragRows();
        boolean forSheetOrTemplate = mDataFile instanceof GURPSCharacter || mDataFile instanceof Template;
        ArrayList<ListRow> process = new ArrayList<>();

        for (Row element : rows) {
            Equipment equipment = new Equipment(mDataFile, (Equipment) element, true);

            model.collectRowsAndSetOwner(list, equipment, false);
            if (forSheetOrTemplate) {
                addRowsToBeProcessed(process, equipment);
            }
        }

        if (forSheetOrTemplate && !process.isEmpty()) {
            EventQueue.invokeLater(new RowPostProcessor(this, process));
        }
    }
}
