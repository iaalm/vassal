/*
 *
 * Copyright (c) 2004-2009 by Rodney Kinney, Joel Uckelman
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */

package VASSAL.build.module.map;

import VASSAL.build.AbstractConfigurable;
import VASSAL.build.AbstractFolder;
import VASSAL.build.AutoConfigurable;
import VASSAL.build.BadDataReport;
import VASSAL.build.Buildable;
import VASSAL.build.Configurable;
import VASSAL.build.GameModule;
import VASSAL.build.module.GameComponent;
import VASSAL.build.module.Map;
import VASSAL.build.module.NewGameIndicator;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.map.boardPicker.Board;
import VASSAL.build.module.map.boardPicker.board.MapGrid;
import VASSAL.build.module.map.boardPicker.board.MapGrid.BadCoords;
import VASSAL.build.module.map.boardPicker.board.ZonedGrid;
import VASSAL.build.widget.PieceSlot;
import VASSAL.command.Command;
import VASSAL.configure.AutoConfigurer;
import VASSAL.configure.Configurer;
import VASSAL.configure.TranslatableStringEnum;
import VASSAL.configure.ValidationReport;
import VASSAL.configure.VisibilityCondition;
import VASSAL.counters.GamePiece;
import VASSAL.counters.PieceCloner;
import VASSAL.counters.Properties;
import VASSAL.counters.Stack;
import VASSAL.i18n.ComponentI18nData;
import VASSAL.i18n.Resources;
import VASSAL.tools.AdjustableSpeedScrollPane;
import VASSAL.tools.ErrorDialog;
import VASSAL.tools.UniqueIdManager;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.menu.MenuManager;
import VASSAL.tools.swing.SwingUtils;
import org.apache.commons.lang3.SystemUtils;

import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is the "At-Start Stack" component, which initializes a Map or Board with a specified stack.
 * Because it uses a regular stack, this component is better suited for limited-force-pool collections
 * of counters than a {@link DrawPile}
 */
public class SetupStack extends AbstractConfigurable implements GameComponent, UniqueIdManager.Identifyable {
  private static final UniqueIdManager idMgr = new UniqueIdManager("SetupStack"); //NON-NLS
  public static final String COMMAND_PREFIX = "SETUP_STACK\t"; //NON-NLS
  protected Point pos = new Point();
  public static final String OWNING_BOARD = "owningBoard"; //NON-NLS
  public static final String X_POSITION = "x"; //NON-NLS
  public static final String Y_POSITION = "y"; //NON-NLS
  protected Buildable parent;

  protected Map map; // No longer used - for binary compatibility only

  protected String owningBoardName;
  protected String id;
  public static final String NAME = "name"; //NON-NLS
  protected static NewGameIndicator indicator;

  protected StackConfigurer stackConfigurer;
  protected JButton configureButton;
  protected String location;
  protected boolean useGridLocation;
  public static final String LOCATION = "location"; //NON-NLS
  public static final String USE_GRID_LOCATION = "useGridLocation"; //NON-NLS

  public static boolean showOthers = false; // Whether we also show other decks/stacks when changing the position of one

  public static boolean isShowOthers() {
    return showOthers;
  }

  public static void setShowOthers(boolean b) {
    showOthers = b;
  }

  protected static String usedBoardWildcard = ""; // Forces "any board" stacks to prefer to match a specific name. Used during configurer draw cycle only.
  protected static String cachedBoard = ""; // Most recently cached board for stack configurer

  public static String getUsedBoardWildcard() {
    return usedBoardWildcard;
  }
  public static String getCachedBoard() {
    return cachedBoard;
  }

  public static void setUsedBoardWildcard(String s) {
    usedBoardWildcard = s;
  }
  public static void setCachedBoard(String s) {
    cachedBoard = s;
  }

  @Override
  public VisibilityCondition getAttributeVisibility(String name) {
    if (USE_GRID_LOCATION.equals(name)) {
      return () -> {
        final Board b = getConfigureBoard(true);
        return b != null && b.getGrid() != null;
      };
    }
    else if (LOCATION.equals(name)) {
      return this::isUseGridLocation;
    }
    else if (X_POSITION.equals(name) || Y_POSITION.equals(name)) {
      return () -> !isUseGridLocation();
    }
    else
      return super.getAttributeVisibility(name);
  }

  // must have a usable board with a grid
  protected boolean isUseGridLocation() {
    if (!useGridLocation) {
      return false;
    }
    final Board b = getConfigureBoard(true);
    return b != null && b.getGrid() != null;
  }

  // only update the position if we're using the location name
  protected void updatePosition() {
    if (isUseGridLocation() && location != null && !location.equals("")) {
      final MapGrid grid = getConfigureBoard(true).getGrid();
      try {
        pos = grid.getLocation(location);
      }
      catch (final BadCoords e) {
        // Allow SetupStacks to match literal grid location names in Irregular/Region grids even if Zone configured to only use/report Zone's name
        if (grid instanceof ZonedGrid) {
          final Point p = ((ZonedGrid) grid).getRegionLocation(location);
          if (p != null) {
            pos = p;
            return;
          }
        }

        ErrorDialog.dataWarning(new BadDataReport(this, "Error.setup_stack_position_error", location, e));
      }
    }
  }

  @Override
  public void validate(Buildable target, ValidationReport report) {
    if (isUseGridLocation()) {
      if (location == null) {
        report.addWarning(getConfigureName() + Resources.getString("SetupStack.null_location")); //NON-NLS
      }
      else {
        final MapGrid grid = getConfigureBoard(true).getGrid();
        try {
          grid.getLocation(location);
        }
        catch (final BadCoords e) {
          // Allow SetupStacks to match literal grid location names in Irregular/Region grids even if Zone configured to only use/report Zone's name
          if (grid instanceof ZonedGrid) {
            final Point p = ((ZonedGrid) grid).getRegionLocation(location);
            if (p != null) {
              return;
            }
          }

          String msg = "Bad location name " + location + " in " + getConfigureName();  //NON-NLS
          if (e.getMessage() != null) {
            msg += ":  " + e.getMessage();
          }
          report.addWarning(msg);
        }
      }
    }

    super.validate(target, report);
  }

  protected void updateLocation() {
    final Board b = getConfigureBoard(true);
    if (b != null) {
      final MapGrid g = b.getGrid();
      if (g != null)
        location = g.locationName(pos);
    }
  }

  @Override
  public void setup(boolean gameStarting) {
    if (gameStarting && indicator.isNewGame() && isOwningBoardActive()) {
      final Stack s = initializeContents();
      updatePosition();
      final Point p = new Point(pos);
      final Map map = getMap();
      // If the Stack belongs to a specific Board, offset the position by the origin of the Board
      // Otherwise, offset the position by the amount of Edge padding specified by the map (i.e. the origin of the top left board)
      if (owningBoardName == null) {
        p.translate(map.getEdgeBuffer().width, map.getEdgeBuffer().height);
      }
      else {
        final Rectangle r = map.getBoardByName(owningBoardName).bounds();
        p.translate(r.x, r.y);
      }
      if (placeNonStackingSeparately()) {
        for (int i = 0; i < s.getPieceCount(); ++i) {
          final GamePiece piece = s.getPieceAt(i);
          if (Boolean.TRUE.equals(piece.getProperty(Properties.NO_STACK))) {
            s.remove(piece);
            piece.setParent(null);
            map.placeAt(piece, p);
            i--;
          }
        }
      }
      map.placeAt(s, p);

      // Tell any *stacked* pieces what map they are on. 
      for (final GamePiece piece : s.asList()) {
        piece.setMap(map);
      }
    }
  }

  protected boolean placeNonStackingSeparately() {
    return true;
  }

  public String getOwningBoardName() {
    return owningBoardName;
  }

  public void setOwningBoardName(String n) {
    owningBoardName = n;
  }

  @Override
  public Command getRestoreCommand() {
    return null;
  }

  @Override
  public String[] getAttributeDescriptions() {
    return new String[]{
      Resources.getString(Resources.NAME_LABEL),
      Resources.getString("Editor.StartStack.board"), //$NON-NLS-1$
      Resources.getString("Editor.StartStack.grid"), //$NON-NLS-1$
      Resources.getString("Editor.StartStack.location"), //$NON-NLS-1$
      Resources.getString("Editor.x_position"), //$NON-NLS-1$
      Resources.getString("Editor.y_position"), //$NON-NLS-1$
    };
  }

  @Override
  public Class<?>[] getAttributeTypes() {
    return new Class<?>[]{
      String.class,
      OwningBoardPrompt.class,
      Boolean.class,
      String.class,
      Integer.class,
      Integer.class
    };
  }

  @Override
  public String[] getAttributeNames() {
    return new String[]{
      NAME,
      OWNING_BOARD,
      USE_GRID_LOCATION,
      LOCATION,
      X_POSITION,
      Y_POSITION
    };
  }

  @Override
  public String getAttributeValueString(String key) {
    if (NAME.equals(key)) {
      return name;
    }
    else if (OWNING_BOARD.equals(key)) {
      return owningBoardName;
    }
    else if (USE_GRID_LOCATION.equals(key)) {
      return Boolean.toString(useGridLocation);
    }
    else if (LOCATION.equals(key)) {
      return location;
    }
    else if (X_POSITION.equals(key)) {
      return String.valueOf(pos.x);
    }
    else if (Y_POSITION.equals(key)) {
      return String.valueOf(pos.y);
    }
    else {
      return null;
    }
  }

  @Override
  public void setAttribute(String key, Object value) {
    if (NAME.equals(key)) {
      setConfigureName((String) value);
    }
    else if (OWNING_BOARD.equals(key)) {
      if (OwningBoardPrompt.ANY.equals(value)) {
        final Map map = getMap();
        if (map != null) {
          final List<String> selectedBoardNames = map.getBoardPicker().getSelectedBoardNames();
          owningBoardName = selectedBoardNames.isEmpty() ? null : selectedBoardNames.get(0);
        }
        else {
          owningBoardName = null;
        }
      }
      else {
        owningBoardName = (String) value;
      }
      updateConfigureButton();
    }
    else if (USE_GRID_LOCATION.equals(key)) {
      if (value instanceof String) {
        value = Boolean.valueOf((String) value);
      }
      useGridLocation = (Boolean) value;
    }
    else if (LOCATION.equals(key)) {
      location = (String) value;
    }
    else if (X_POSITION.equals(key)) {
      if (value instanceof String) {
        value = Integer.valueOf((String) value);
      }
      pos.x = (Integer) value;
    }
    else if (Y_POSITION.equals(key)) {
      if (value instanceof String) {
        value = Integer.valueOf((String) value);
      }
      pos.y = (Integer) value;
    }
  }

  /**
   * Setup Stacks with no name will display in editor w/ the name of something inside them that does have a name.
   * @return Configure name of the Setup Stack
   */
  @Override
  public String getConfigureName() {
    final StringBuilder sb = new StringBuilder("");

    if ((name != null) && !name.isEmpty()) {
      sb.append(name);
    }
    final Configurable[] configurables = getConfigureComponents();
    for (final Configurable c : configurables) {
      final String cName = c.getConfigureName();
      if ((cName != null) && !cName.isEmpty()) {
        if (sb.length() > 0) {
          sb.append(" - ");
        }
        sb.append(cName);
        break;
      }
    }
    if (useGridLocation) {
      if (location != null) {
        if (sb.length() > 0) {
          sb.append(" - ");
        }
        sb.append(location);
      }
    }
    else {
      if (sb.length() > 0) {
        sb.append(" - ");
      }
      sb.append('(');
      sb.append(pos.x);
      sb.append(',');
      sb.append(pos.y);
      sb.append(')');
    }
    return sb.toString();
  }

  @Override
  public void add(Buildable child) {
    super.add(child);
    updateConfigureButton();
  }

  public Map getMap() {
    return (Map)getNonFolderAncestor();
  }

  @Override
  public void addTo(Buildable parent) {
    if (indicator == null) {
      indicator = new NewGameIndicator(COMMAND_PREFIX);
    }
    idMgr.add(this);

    GameModule.getGameModule().getGameState().addGameComponent(this);
    setAttributeTranslatable(NAME, false);
  }

  @Override
  public Class<?>[] getAllowableConfigureComponents() {
    return new Class<?>[]{PieceSlot.class};
  }

  @Override
  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("SetupStack.html"); //NON-NLS
  }

  public static String getConfigureTypeName() {
    return Resources.getString("Editor.StartStack.component_type"); //$NON-NLS-1$
  }

  @Override
  public void removeFrom(Buildable parent) {
    idMgr.remove(this);
    GameModule.getGameModule().getGameState().removeGameComponent(this);
  }

  protected boolean isOwningBoardActive() {
    boolean active = false;
    if (owningBoardName == null) {
      active = true;
    }
    else {
      final Map map = getMap();
      if ((map != null) && (map.getBoardByName(owningBoardName) != null)) {
        active = true;
      }
    }
    return active;
  }

  /**
   * Traverses children components recursively (to support folder structure), adding any pieces found in
   * PieceSlot objects to our stack.
   * @param s The stack we are creating
   * @param c Next layer of configurables to traverse
   * @param num Incoming pieceslot count for error reporting
   * @return resulting pieceslot count for error reporting
   */
  protected int recursiveInitializeContents(Stack s, Configurable[] c, int num) {
    for (final Configurable configurable : c) {
      if (configurable instanceof PieceSlot) {
        num++;
        final PieceSlot slot = (PieceSlot) configurable;
        slot.clearCache(); //BR// Always rebuild piece at beginning - we might be starting a new game in Editor after changing prototypes
        GamePiece p = slot.getPiece();

        if (p != null) { // In case slot fails to "build the piece", which is a possibility.
          p = PieceCloner.getInstance().clonePiece(p);
          GameModule.getGameModule().getGameState().addPiece(p);
          s.add(p);
        }
        else {
          ErrorDialog.dataWarning(new BadDataReport(slot, Resources.getString("Error.build_piece_at_start_stack", num, getConfigureName()), slot.getPieceDefinition()));
        }
      }
      else if (configurable instanceof AbstractFolder) {
        final Configurable[] cDeeper = configurable.getConfigureComponents();
        num = recursiveInitializeContents(s, cDeeper, num);
      }
    }

    return num;
  }

  protected Stack initializeContents() {
    final Stack s = createStack();
    final Configurable[] c = getConfigureComponents();
    recursiveInitializeContents(s, c, 0);
    GameModule.getGameModule().getGameState().addPiece(s);
    return s;
  }

  protected Stack createStack() {
    return new Stack();
  }

  @Override
  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getId() {
    return id;
  }

  public List<String> getValidOwningBoards() {
    final ArrayList<String> l = new ArrayList<>();
    final Map m = getMap();
    if (m != null) {
      l.addAll(Arrays.asList(m.getBoardPicker().getAllowableBoardNames()));
    }
    else {
      for (final Map m2 : Map.getMapList()) {
        l.addAll(
          Arrays.asList(m2.getBoardPicker().getAllowableBoardNames()));
      }
    }
    return l;
  }

  public static class OwningBoardPrompt extends TranslatableStringEnum {
    public static final String ANY = "<any>"; //NON-NLS
    public static final String ANY_NAME = Resources.getString("Editor.SetupStack.any_name");

    /**
     * For this one we need to use pre-translated display names.
     * @return true
     */
    @Override
    public boolean isDisplayNames() {
      return true;
    }

    @Override
    public String[] getValidValues(AutoConfigurable target) {
      final String[] values;
      if (target instanceof SetupStack) {
        final ArrayList<String> l = new ArrayList<>();
        l.add(ANY);
        l.addAll(((SetupStack) target).getValidOwningBoards());

        // If we've been left with an illegal board (e.g. the board got deleted or we got cut-and-paste to a map without it), at least
        // show that we have the wrong value when someone brings up the configurer. (This also lets it be changed to "any" rather than
        // spuriously showing it is already set to "any").
        final String owning = ((SetupStack) target).getOwningBoardName();
        if ((owning != null) && !l.contains(owning)) {
          l.add(owning);
        }

        values = l.toArray(new String[0]);
      }
      else {
        values = new String[]{ANY};
      }
      return values;
    }

    @Override
    public String[] getI18nKeys(AutoConfigurable target) {
      final String[] values;
      if (target instanceof SetupStack) {
        final ArrayList<String> l = new ArrayList<>();
        final ArrayList<String> lkey = new ArrayList<>();
        l.add(ANY_NAME);
        lkey.add(ANY);
        final Map m = ((SetupStack) target).getMap();
        if (m != null) {
          l.addAll(Arrays.asList(m.getBoardPicker().getAllowableLocalizedBoardNames()));
          lkey.addAll(Arrays.asList(m.getBoardPicker().getAllowableBoardNames()));
        }
        else {
          for (final Map m2 : Map.getMapList()) {
            l.addAll(
              Arrays.asList(m2.getBoardPicker().getAllowableLocalizedBoardNames()));
            lkey.addAll(
              Arrays.asList(m2.getBoardPicker().getAllowableBoardNames()));
          }
        }

        // If we've been left with an illegal board (e.g. the board got deleted or we got cut-and-paste to a map without it), at least
        // show that we have the wrong value when someone brings up the configurer (this also lets it be changed to "any" rather than
        // spuriously showing it is already set to "any")
        final String owning = ((SetupStack) target).getOwningBoardName();
        if ((owning != null) && !lkey.contains(owning)) {
          l.add(Resources.getString("Editor.SetupStack.no_such_board", owning));
        }

        values = l.toArray(new String[0]);
      }
      else {
        values = new String[]{ANY_NAME};
      }
      return values;
    }
  }

  /*
   *  GUI Stack Placement Configurer
   */
  protected Configurer xConfig, yConfig, locationConfig;

  @Override
  public Configurer getConfigurer() {
    config = null; // Don't cache the Configurer so that the list of available boards won't go stale
    final Configurer c = super.getConfigurer();
    xConfig = ((AutoConfigurer) c).getConfigurer(X_POSITION);
    yConfig = ((AutoConfigurer) c).getConfigurer(Y_POSITION);
    locationConfig = ((AutoConfigurer) c).getConfigurer(LOCATION);
    updateConfigureButton();
    ((Container) c.getControls()).add(configureButton);

    return c;
  }

  protected void updateConfigureButton() {
    if (configureButton == null) {
      configureButton = new JButton(Resources.getString("Editor.SetupStack.reposition_stack"));
      configureButton.addActionListener(e -> configureStack());
    }
    configureButton.setEnabled(getConfigureBoard(true) != null && buildComponents.size() > 0);
  }

  public void prepareConfigurer(Board board) {
    if ((stackConfigurer == null) || (stackConfigurer.board != board)) {
      stackConfigurer = new StackConfigurer(this);
      stackConfigurer.board = getConfigureBoard(true);
      stackConfigurer.cacheBoundingBox();
      updatePosition();
    }
  }

  protected void configureStack() {
    stackConfigurer = new StackConfigurer(this);
    stackConfigurer.init();
    stackConfigurer.setVisible(true);
    stackConfigurer.cacheBoundingBox();
  }

  protected PieceSlot getTopPiece() {
    final Iterator<PieceSlot> i =
      getAllDescendantComponentsOf(PieceSlot.class).iterator();
    return i.hasNext() ? i.next() : null;
  }


  /*
   * Return a board to configure the stack on.
   * @param checkSelectedBoards If true, prefer a board the player has actually selected from the menu over simply the top one in the list.
   */
  public Board getConfigureBoard(boolean checkSelectedBoards) {
    Board board = null;

    final Map map = getMap();

    if (map != null && !OwningBoardPrompt.ANY.equals(owningBoardName)) {
      board = map.getBoardPicker().getBoard(owningBoardName);
    }

    if (board == null && map != null) {
      if (checkSelectedBoards) {
        // During configuration with no live player window, if we're drawing multiple stacks, force ghost stacks to prefer to match the active stack's board pick
        final String wildcard = SetupStack.getUsedBoardWildcard();
        if (!wildcard.isEmpty()) {
          // We can't use map.getBoardByName() because Player window might not be live (in which case it will just return null)
          if (map.getBoardPicker() != null) {
            for (final Board b : map.getBoardPicker().possibleBoards) {
              if (b.getName().equals(wildcard)) {
                board = b;
                break;
              }
            }
          }
        }

        //BR// If we're doing the start-of-game setup (as opposed to just configuring it), prefer a "selected" board to the first one in the list.
        if (board == null) {
          final BoardPicker boardPicker = map.getBoardPicker();
          if (boardPicker != null) {
            final List<String> selectedBoards = boardPicker.getSelectedBoardNames();
            for (final String s : selectedBoards) {
              board = map.getBoardByName(s);
              if (board != null) {
                break;
              }
            }
          }
        }
      }

      // Final fallback (and default) is to just take the first board from the picker
      if (board == null) {
        final String[] allBoards = map.getBoardPicker().getAllowableBoardNames();
        if (allBoards.length > 0) {
          board = map.getBoardPicker().getBoard(allBoards[0]);
        }
      }
    }

    return board;
  }


  /*
   * Return a board to configure the stack on.
   */
  protected Board getConfigureBoard() {
    return getConfigureBoard(false);
  }


  protected static final Dimension DEFAULT_SIZE = new Dimension(800, 600);
  protected static final int DELTA = 1;
  protected static final int FAST = 10;
  protected static final int FASTER = 5;
  protected static final int DEFAULT_DUMMY_SIZE = 50;

  public class StackConfigurer extends JFrame implements ActionListener, KeyListener, MouseListener {

    private static final long serialVersionUID = 1L;

    protected Board board;
    protected View view;
    protected JScrollPane scroll;
    protected SetupStack myStack;
    protected PieceSlot mySlot;
    protected GamePiece myPiece;
    protected Point savePosition;
    protected Dimension dummySize;
    protected BufferedImage dummyImage;
    protected JLabel coords;
    protected JCheckBox shouldShowOthers;
    protected Rectangle cachedBoundingBox;

    public StackConfigurer(SetupStack stack) {
      super(Resources.getString("Editor.SetupStack.adjust_at_start_stack"));
      setJMenuBar(MenuManager.getInstance().getMenuBarFor(this));

      myStack = stack;
      mySlot = stack.getTopPiece();
      if (mySlot != null) {
        myPiece = mySlot.getPiece();
      }

      myStack.updatePosition();
      savePosition = new Point(myStack.pos);

      if (stack instanceof DrawPile) {
        dummySize = new Dimension(((DrawPile) stack).getSize());
      }
      else {
        dummySize = new Dimension(DEFAULT_DUMMY_SIZE, DEFAULT_DUMMY_SIZE);
      }

      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {
          cancel();
        }
      });
    }

    // Main Entry Point
    protected void init() {

      board = getConfigureBoard(true);

      view = new View(board, myStack);

      view.addKeyListener(this);
      view.addMouseListener(this);
      view.setFocusable(true);

      scroll =
          new AdjustableSpeedScrollPane(
              view,
              JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
              JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

      scroll.setPreferredSize(DEFAULT_SIZE);

      add(scroll, BorderLayout.CENTER);

      final Box textPanel = Box.createVerticalBox();
      coords = new JLabel(myStack.pos.x + ", " + myStack.pos.y);
      textPanel.add(coords);
      textPanel.add(new JLabel(Resources.getString("Editor.SetupStack.arrow_keys_move_stack")));
      textPanel.add(new JLabel(Resources.getString(SystemUtils.IS_OS_MAC ? "Editor.SetupStack.shift_command_keys_move_stack_faster_mac" : "Editor.SetupStack.ctrl_shift_keys_move_stack_faster")));

      final Box displayPanel = Box.createHorizontalBox();
      displayPanel.add(Box.createRigidArea(new Dimension(10, 10)));
      shouldShowOthers = new JCheckBox(Resources.getString("Editor.SetupStack.show_others"), SetupStack.isShowOthers());
      displayPanel.add(shouldShowOthers);
      shouldShowOthers.addItemListener(e -> {
        SetupStack.setShowOthers(shouldShowOthers.isSelected());
        repaint();
      });
      displayPanel.add(Box.createRigidArea(new Dimension(10, 10)));

      final Box buttonPanel = Box.createHorizontalBox();
      final JButton snapButton = new JButton(Resources.getString("Editor.SetupStack.snap_to_grid"));
      snapButton.addActionListener(e -> {
        snap();
        view.grabFocus();
      });
      buttonPanel.add(snapButton);

      final JButton okButton = new JButton(Resources.getString("General.ok"));
      okButton.addActionListener(e -> {
        setVisible(false);
        // Update the Component configurer to reflect the change
        xConfig.setValue(String.valueOf(myStack.pos.x));
        yConfig.setValue(String.valueOf(myStack.pos.y));
        if ((locationConfig != null) && !useGridLocation) { // DrawPile's do not have a location. And don't need to change location if it's fixed to a grid location.
          updateLocation();
          locationConfig.setValue(location);
        }
      });
      final JPanel okPanel = new JPanel();
      okPanel.add(okButton);

      final JButton canButton = new JButton(Resources.getString("General.cancel"));
      canButton.addActionListener(e -> {
        cancel();
        setVisible(false);
      });
      okPanel.add(canButton);

      final Box controlPanel = Box.createHorizontalBox();
      controlPanel.add(textPanel);
      controlPanel.add(displayPanel);
      controlPanel.add(buttonPanel);

      final Box mainPanel = Box.createVerticalBox();
      mainPanel.add(controlPanel);
      mainPanel.add(okPanel);

      add(mainPanel, BorderLayout.SOUTH);

      // Default actions on Enter/ESC
      SwingUtils.setDefaultButtons(getRootPane(), okButton, canButton);

      scroll.revalidate();
      updateDisplay();
      pack();
      repaint();
    }

    public void setShowOthers(boolean show) {
      SetupStack.showOthers = show;
    }

    public void updateCoords(String text) {
      coords.setText(text);
    }

    public void updateCoords() {
      coords.setText(myStack.pos.x + ", " + myStack.pos.y);
    }

    protected void cancel() {
      myStack.pos.x = savePosition.x;
      myStack.pos.y = savePosition.y;
    }

    public void updateDisplay() {
      if (!view.getVisibleRect().contains(myStack.pos)) {
        view.center(new Point(myStack.pos.x, myStack.pos.y));
      }
    }

    protected void snap() {
      final MapGrid grid = board.getGrid();
      if (grid != null) {
        final Point snapTo = grid.snapTo(pos);
        pos.x = snapTo.x;
        pos.y = snapTo.y;
        updateCoords();
        updateDisplay();
        repaint();
      }
    }

    public JScrollPane getScroll() {
      return scroll;
    }

    /*
     * If the piece to be displayed does not have an Image, then we
     * need to supply a dummy one.
     */
    public BufferedImage getDummyImage() {
      if (dummyImage == null) {
        dummyImage = ImageUtils.createCompatibleTranslucentImage(
          dummySize.width * 2, dummySize.height * 2);
        final Graphics2D g = dummyImage.createGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, dummySize.width, dummySize.height);
        g.setColor(Color.black);
        g.drawRect(0, 0, dummySize.width, dummySize.height);
        g.dispose();
      }
      return dummyImage;
    }

    public void drawDummyImage(Graphics g, int x, int y) {
      drawDummyImage(g, x - dummySize.width / 2, y - dummySize.height / 2, null, 1.0);
    }

    public void drawDummyImage(Graphics g, int x, int y, Component obs, double zoom) {
      final Graphics2D g2d = (Graphics2D) g;
      final AffineTransform orig_t = g2d.getTransform();
      final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();
      final AffineTransform scaled_t = new AffineTransform(orig_t);
      scaled_t.scale(os_scale, os_scale);
      g2d.setTransform(scaled_t);

      x /= os_scale;
      y /= os_scale;

      g.drawImage(getDummyImage(), x, y, obs);

      g2d.setTransform(orig_t);
    }

    public void drawImage(Graphics g, int x, int y, Component obs, double zoom) {
      final Rectangle r = myPiece == null ? null : myPiece.boundingBox();
      if (r == null || r.width == 0 || r.height == 0) {
        drawDummyImage(g, x, y);
      }
      else {
        myPiece.draw(g, x, y, obs, zoom);
      }
    }

    public Rectangle getPieceBoundingBox() {
      final Rectangle r = myPiece == null ? new Rectangle() : myPiece.boundingBox();
      if (r.width == 0 || r.height == 0) {
        r.width = dummySize.width;
        r.height = dummySize.height;
        r.x = -r.width / 2;
        r.y = -r.height / 2;
      }

      return r;
    }

    public void cacheBoundingBox() {
      cachedBoundingBox = getPieceBoundingBox();
    }

    public Rectangle getCachedBoundingBox() {
      return cachedBoundingBox;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

    }

    @Override
    public void keyPressed(KeyEvent e) {

      switch (e.getKeyCode()) {
      case KeyEvent.VK_UP:
        adjustY(-1, e);
        break;
      case KeyEvent.VK_DOWN:
        adjustY(1, e);
        break;
      case KeyEvent.VK_LEFT:
        adjustX(-1, e);
        break;
      case KeyEvent.VK_RIGHT:
        adjustX(1, e);
        break;
      case KeyEvent.VK_ENTER:
      case KeyEvent.VK_ESCAPE:
        return; // Prevent these unlikely-and-inadvisable-anyway hotkeys from interfering with dialog defaults
      default :
        if (myPiece != null) {
          myPiece.keyEvent(SwingUtils.getKeyStrokeForEvent(e));
        }
        break;
      }
      updateDisplay();
      repaint();
      e.consume();
    }

    protected void adjustX(int direction, KeyEvent e) {
      int delta = direction * DELTA;
      if (e.isShiftDown()) {
        delta *= FAST;
      }
      if (SwingUtils.isModifierKeyDown(e)) {
        delta *= FASTER;
      }
      int newX = myStack.pos.x + delta;
      if (newX < 0) newX = 0;
      if (newX >= board.getSize().getWidth()) newX = (int) board.getSize().getWidth() - 1;
      myStack.pos.x = newX;
      updateCoords();
    }

    protected void adjustY(int direction, KeyEvent e) {
      int delta = direction * DELTA;
      if (e.isShiftDown()) {
        delta *= FAST;
      }
      if (SwingUtils.isModifierKeyDown(e)) {
        delta *= FASTER;
      }
      int newY = myStack.pos.y + delta;
      if (newY < 0) newY = 0;
      if (newY >= board.getSize().getHeight()) newY = (int) board.getSize().getHeight() - 1;
      myStack.pos.y = newY;
      updateCoords();
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    protected void maybePopup(MouseEvent e) {
      if (!e.isPopupTrigger() || myPiece == null) {
        return;
      }

      final Rectangle r = getPieceBoundingBox();
      r.translate(pos.x, pos.y);
      if (r.contains(e.getPoint())) {
        final JPopupMenu popup = MenuDisplayer.createPopup(myPiece);
        if (popup != null) {
          popup.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent evt) {
              view.repaint();
            }

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent evt) {
              view.repaint();
            }

            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent evt) {
            }
          });
          if (view.isShowing()) {
            popup.show(view, e.getX(), e.getY());
          }
        }
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      maybePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      maybePopup(e);
    }
  }

  @Override
  public ComponentI18nData getI18nData() {
    final ComponentI18nData myI18nData = super.getI18nData();
    myI18nData.setAttributeTranslatable(LOCATION, false);
    return myI18nData;
  }

// FIXME: check for duplication with PieceMover

  public static class View extends JPanel implements DropTargetListener, DragGestureListener, DragSourceListener, DragSourceMotionListener {

    private static final long serialVersionUID = 1L;
    protected static final int CURSOR_ALPHA = 127;
    protected static final int EXTRA_BORDER = 4;
    protected Board myBoard;
    protected MapGrid myGrid;
    protected SetupStack myStack;
    protected GamePiece myPiece;
    protected PieceSlot slot;
    protected DragSource ds = DragSource.getDefaultDragSource();
    protected boolean isDragging = false;
    protected JLabel dragCursor;
    protected JLayeredPane drawWin;
    protected Point drawOffset = new Point();
    protected Rectangle boundingBox;
    protected int currentPieceOffsetX;
    protected int currentPieceOffsetY;
    protected int originalPieceOffsetX;
    protected int originalPieceOffsetY;
    protected Point lastDragLocation = new Point();

    protected List<SetupStack> otherStacks;

    public View(Board b, SetupStack s) {
      myBoard = b;
      myGrid = b.getGrid();
      myStack = s;
      slot = myStack.getTopPiece();
      if (slot != null) {
        myPiece = slot.getPiece();
      }
      new DropTarget(this, DnDConstants.ACTION_MOVE, this);
      ds.createDefaultDragGestureRecognizer(this,
        DnDConstants.ACTION_MOVE, this);
      setFocusTraversalKeysEnabled(false);

      findOtherStacks();
    }

    private void prepareOtherConfigurers() {
      SetupStack.setUsedBoardWildcard(myBoard.getName()); //This will make "any board" stacks match our selected board
      SetupStack.setCachedBoard(myBoard.getName());
      for (final SetupStack s : otherStacks) {
        s.prepareConfigurer(myBoard);
      }
      SetupStack.setUsedBoardWildcard("");
    }

    private void findOtherStacks() {
      otherStacks = GameModule
        .getGameModule()
        .getAllDescendantComponentsOf(SetupStack.class)
        .stream()
        // don't draw this stack or stacks from other maps
        .filter(s -> s != myStack && s.getMap() == myStack.getMap())
        // don't draw stacks from wrong board
        .filter(s -> {
          if (s.owningBoardName != null) {
            if (myStack.owningBoardName == null) {
              if (!s.owningBoardName.equals(myBoard.getName())) {
                return false;
              }
            }

            if (!s.owningBoardName.equals(myStack.owningBoardName)) {
              return false; 
            }
          }

          return true;
        })
        .collect(Collectors.toList());

      if (isShowOthers()) {
        prepareOtherConfigurers();
      }
    }

    private void drawOtherStack(SetupStack s, Graphics2D g, Rectangle vrect, double os_scale) {
      final int x = (int)(s.pos.x * os_scale);
      final int y = (int)(s.pos.y * os_scale);

      final Rectangle bb = new Rectangle(s.stackConfigurer.getCachedBoundingBox());
      bb.x += x;
      bb.y += y;

      if (vrect.intersects(bb)) {
        s.stackConfigurer.drawImage(g, x, y, null, os_scale);
      }
    }

    @Override
    public void paint(Graphics g) {
      // Since this configurer is non-modal, there is always the possibility that user is switching back and forth.
      // In which case they get to eat the not-inconsequential perf hit of re-whatevering all the configurers.
      if (isShowOthers() && !myBoard.getName().equals(getCachedBoard())) {
        prepareOtherConfigurers();
      }

      final Graphics2D g2d = (Graphics2D) g;

      g2d.addRenderingHints(SwingUtils.FONT_HINTS);
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                           RenderingHints.VALUE_ANTIALIAS_ON);

      // ensure that the background is repainted
      super.paint(g);

      final double os_scale = g2d.getDeviceConfiguration().getDefaultTransform().getScaleX();

      final AffineTransform orig_t = g2d.getTransform();
      g2d.setTransform(SwingUtils.descaleTransform(orig_t));

      myBoard.draw(g, 0, 0, os_scale, this);
      if (myGrid != null) {
        final Rectangle bounds = new Rectangle(new Point(), myBoard.bounds().getSize());
        bounds.width *= os_scale;
        bounds.height *= os_scale;
        myGrid.draw(g, bounds, bounds, os_scale, false);
      }

      if (isShowOthers()) {
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5F));

        final Rectangle r = getVisibleRect();
        r.x *= os_scale;
        r.y *= os_scale;
        r.width *= os_scale;
        r.height *= os_scale;

        for (final SetupStack s : otherStacks) { 
          drawOtherStack(s, g2d, r, os_scale);
        }
      }

      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP));

      final int x = (int)(myStack.pos.x * os_scale);
      final int y = (int)(myStack.pos.y * os_scale);
      myStack.stackConfigurer.drawImage(g, x, y, this, os_scale);

      g2d.setTransform(orig_t);
    }

    @Override
    public void update(Graphics g) {
      // To avoid flicker, don't clear the display first *
      paint(g);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(
          myBoard.bounds().width,
          myBoard.bounds().height);
    }

    public void center(Point p) {
      final Rectangle r = this.getVisibleRect();
      if (r.width == 0) {
        r.width = DEFAULT_SIZE.width;
        r.height = DEFAULT_SIZE.height;
      }
      int x = p.x - r.width / 2;
      int y = p.y - r.height / 2;
      if (x < 0) x = 0;
      if (y < 0) y = 0;
      scrollRectToVisible(new Rectangle(x, y, r.width, r.height));
    }

    @Override
    public void dragEnter(DropTargetDragEvent arg0) {
    }

    @Override
    public void dragOver(DropTargetDragEvent e) {
      scrollAtEdge(e.getLocation(), 15);

      final Point pos = e.getLocation();
      pos.translate(currentPieceOffsetX, currentPieceOffsetY);
      myStack.stackConfigurer.updateCoords(pos.x + ", " + pos.y);
    }

    public void scrollAtEdge(Point evtPt, int dist) {
      final JScrollPane scroll = myStack.stackConfigurer.getScroll();

      final Point p = new Point(evtPt.x - scroll.getViewport().getViewPosition().x,
          evtPt.y - scroll.getViewport().getViewPosition().y);
      int dx = 0, dy = 0;
      if (p.x < dist && p.x >= 0)
        dx = -1;
      if (p.x >= scroll.getViewport().getSize().width - dist
          && p.x < scroll.getViewport().getSize().width)
        dx = 1;
      if (p.y < dist && p.y >= 0)
        dy = -1;
      if (p.y >= scroll.getViewport().getSize().height - dist
          && p.y < scroll.getViewport().getSize().height)
        dy = 1;

      if (dx != 0 || dy != 0) {
        Rectangle r = new Rectangle(scroll.getViewport().getViewRect());
        r.translate(2 * dist * dx, 2 * dist * dy);
        r = r.intersection(new Rectangle(new Point(0, 0), getPreferredSize()));
        scrollRectToVisible(r);
      }
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent arg0) {
    }

    @Override
    public void drop(DropTargetDropEvent event) {
      removeDragCursor();
      final Point pos = event.getLocation();
      pos.translate(currentPieceOffsetX, currentPieceOffsetY);
      myStack.pos.x = pos.x;
      myStack.pos.y = pos.y;
      myStack.stackConfigurer.updateCoords();
      myStack.stackConfigurer.updateDisplay();
      repaint();
    }

    @Override
    public void dragExit(DropTargetEvent arg0) {
    }

    @Override
    public void dragEnter(DragSourceDragEvent arg0) {
    }

    @Override
    public void dragOver(DragSourceDragEvent arg0) {
    }

    @Override
    public void dropActionChanged(DragSourceDragEvent arg0) {
    }

    @Override
    public void dragDropEnd(DragSourceDropEvent arg0) {
      removeDragCursor();
    }

    @Override
    public void dragExit(DragSourceEvent arg0) {
    }

    @Override
    public void dragGestureRecognized(DragGestureEvent dge) {
      if (!SwingUtils.isDragTrigger(dge)) {
        return;
      }

      final Point mousePosition = dge.getDragOrigin();
      final Point piecePosition = new Point(myStack.pos);

      // Check drag starts inside piece
      final Rectangle r = myStack.stackConfigurer.getPieceBoundingBox();
      r.translate(piecePosition.x, piecePosition.y);
      if (!r.contains(mousePosition)) {
        return;
      }

      originalPieceOffsetX = piecePosition.x - mousePosition.x;
      originalPieceOffsetY = piecePosition.y - mousePosition.y;

      drawWin = null;

      makeDragCursor();
      setDragCursor();

      SwingUtilities.convertPointToScreen(mousePosition, drawWin);
      moveDragCursor(mousePosition.x, mousePosition.y);

      // begin dragging
      try {
        dge.startDrag(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                      new StringSelection(""), this); // DEBUG
        dge.getDragSource().addDragSourceMotionListener(this);
      }
      catch (final InvalidDnDOperationException e) {
        ErrorDialog.bug(e);
      }
    }

    protected void setDragCursor() {
      final JRootPane rootWin = SwingUtilities.getRootPane(this);
      if (rootWin != null) {
        // remove cursor from old window
        if (dragCursor.getParent() != null) {
          dragCursor.getParent().remove(dragCursor);
        }
        drawWin = rootWin.getLayeredPane();

        calcDrawOffset();
        dragCursor.setVisible(true);
        drawWin.add(dragCursor, JLayeredPane.DRAG_LAYER);
      }
    }

    /** Moves the drag cursor on the current draw window */
    protected void moveDragCursor(int dragX, int dragY) {
      if (drawWin != null) {
        dragCursor.setLocation(dragX - drawOffset.x, dragY - drawOffset.y);
      }
    }

    private void removeDragCursor() {
      if (drawWin != null) {
        if (dragCursor != null) {
          dragCursor.setVisible(false);
          drawWin.remove(dragCursor);
        }
        drawWin = null;
      }
    }

    /** calculates the offset between cursor dragCursor positions */
    private void calcDrawOffset() {
      if (drawWin != null) {
        // drawOffset is the offset between the mouse location during a drag
        // and the upper-left corner of the cursor
        // accounts for difference between event point (screen coords)
        // and Layered Pane position, boundingBox and off-center drag
        drawOffset.x = -boundingBox.x - currentPieceOffsetX + EXTRA_BORDER;
        drawOffset.y = -boundingBox.y - currentPieceOffsetY + EXTRA_BORDER;
        SwingUtilities.convertPointToScreen(drawOffset, drawWin);
      }
    }

    private void makeDragCursor() {
      //double zoom = 1.0;
      // create the cursor if necessary
      if (dragCursor == null) {
        dragCursor = new JLabel();
        dragCursor.setVisible(false);
      }

      //dragCursorZoom = zoom;
      currentPieceOffsetX = originalPieceOffsetX;
      currentPieceOffsetY = originalPieceOffsetY;

      // Record sizing info and resize our cursor
      boundingBox =  myStack.stackConfigurer.getPieceBoundingBox();
      calcDrawOffset();

      final int w = boundingBox.width + EXTRA_BORDER * 2;
      final int h = boundingBox.height + EXTRA_BORDER * 2;

      final BufferedImage image =
        ImageUtils.createCompatibleTranslucentImage(w, h);

      final Graphics2D g = image.createGraphics();
      g.addRenderingHints(SwingUtils.FONT_HINTS);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                         RenderingHints.VALUE_ANTIALIAS_ON);

      myStack.stackConfigurer.drawImage(
        g,
        EXTRA_BORDER - boundingBox.x,
        EXTRA_BORDER - boundingBox.y, dragCursor, 1.0
      );

      // make the drag image transparent
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_IN));
      g.setColor(new Color(0xFF, 0xFF, 0xFF, CURSOR_ALPHA));
      g.fillRect(0, 0, image.getWidth(), image.getHeight());

      g.dispose();

      dragCursor.setSize(w, h);

      // store the bitmap in the cursor
      dragCursor.setIcon(new ImageIcon(image));
    }

    @Override
    public void dragMouseMoved(DragSourceDragEvent event) {
      if (!event.getLocation().equals(lastDragLocation)) {
        lastDragLocation = event.getLocation();
        moveDragCursor(event.getX(), event.getY());
        if (dragCursor != null && !dragCursor.isVisible()) {
          dragCursor.setVisible(true);
        }
      }
    }
  }

  /**
   * {@link VASSAL.search.SearchTarget}
   * @return a list of the Configurables string/expression fields if any (for search)
   */
  @Override
  public List<String> getExpressionList() {
    if (owningBoardName != null) {
      return List.of(owningBoardName);
    }
    else {
      return Collections.emptyList();
    }
  }
}
