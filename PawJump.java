import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.swing.*;

public class PawJump {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MenuWindow menu = new MenuWindow();
            menu.setVisible(true);
        });
    }
}

class MenuWindow extends JFrame {
    public MenuWindow() {
        setTitle("Paw Jump!");
        setSize(520, 480);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(new Color(30, 30, 60));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setLayout(null);

        // Game logo (show at full size, but scale down if too large for dialog)
        final ImageIcon logoIcon = new ImageIcon("images/pawjump.png");
        final int logoW = logoIcon.getIconWidth();
        final int logoH = logoIcon.getIconHeight();
        final int maxLogoW = 440;
        final int maxLogoH = 160;
        final int[] drawSize = new int[2];
        drawSize[0] = logoW;
        drawSize[1] = logoH;
        if (logoW > maxLogoW || logoH > maxLogoH) {
            double scaleW = maxLogoW / (double)logoW;
            double scaleH = maxLogoH / (double)logoH;
            double scale = Math.min(scaleW, scaleH);
            drawSize[0] = (int)(logoW * scale);
            drawSize[1] = (int)(logoH * scale);
        }
        final int drawW = drawSize[0];
        final int drawH = drawSize[1];
        JLabel logoLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(logoIcon.getImage(), 0, 0, drawW, drawH, this);
                g2.dispose();
            }
        };
        logoLabel.setBounds((520 - drawW) / 2, 60, drawW, drawH);
        panel.add(logoLabel);

        // Play button
        JButton playBtn = new JButton("Play");
        playBtn.setFont(new Font("Arial", Font.BOLD, 32));
        playBtn.setBackground(new Color(60, 180, 255));
        playBtn.setForeground(Color.WHITE);
        playBtn.setFocusPainted(false);
        playBtn.setBounds(160, 260, 200, 70);
        playBtn.addActionListener(e -> {
            // Start the game window and close the menu
            JFrame gameFrame = new JFrame("Paw Jump!");
            gameFrame.setSize(1280, 720);
            gameFrame.setDefaultCloseOperation(EXIT_ON_CLOSE);
            gameFrame.setResizable(false);
            GamePanel panelGame = new GamePanel();
            gameFrame.add(panelGame);
            gameFrame.setLocationRelativeTo(null);
            this.dispose();
            gameFrame.setVisible(true);
            // Request focus for keyboard input
            panelGame.requestFocusInWindow();
        });
        panel.add(playBtn);

        // Highscore label (load from file if not loaded yet)
        int highScoreValue = 0;
        try (BufferedReader br = new BufferedReader(new FileReader("highscore.txt"))) {
            highScoreValue = Integer.parseInt(br.readLine());
        } catch (Exception e) {
            highScoreValue = 0;
        }
        JLabel highScoreLabel = new JLabel("High Score: " + highScoreValue + " m", SwingConstants.CENTER);
        highScoreLabel.setFont(new Font("Arial", Font.BOLD, 26));
        highScoreLabel.setForeground(new Color(255, 215, 0));
        highScoreLabel.setBounds(0, 350, 520, 40);
        panel.add(highScoreLabel);

        setContentPane(panel);
    }
}


class GamePanel extends JPanel implements ActionListener, KeyListener {
    // Removed startDialog and gameStarted logic. GamePanel now starts immediately.
    // Track last spawn score for suplemen to avoid overlap with meat
    private int lastSuplemenSpawnScore = -1000;
    // Shine effect image for suplemen
    private Image shineImg = new ImageIcon("images/shine.png").getImage();
    // Death animation (4 frames)
    private Image deathSheet = new ImageIcon("images/Death.png").getImage();
    private Image[] deathFrames = new Image[4];
    private int deathFrame = 0;
    private int deathFrameTick = 0;
    private final int deathFrameDelay = 8; // slower = longer animation
    private boolean isDying = false;
    private int deathAnimTicks = 0;
    private final int deathAnimDuration = 32; // 4 frames * 8 ticks per frame
    private boolean deathAnimDone = false;
    // Suplemen item
    private Image suplemenImg = new ImageIcon("images/suplemen.png").getImage();
    private int suplemenX = 0, suplemenY = 0, suplemenW = 48, suplemenH = 48;
    private boolean suplemenActive = false;
    private int suplemenPhase = 0;
    private int suplemenAppearCount = 0;
    private boolean suplemenEffectActive = false;
    private boolean suplemenEffectUsed = false;
    // Helper: check if two rectangles are at least minDist pixels apart vertically
    private boolean isVerticallyApart(int y1, int h1, int y2, int h2, int minDist) {
        return (y1 + h1 + minDist <= y2) || (y2 + h2 + minDist <= y1);
    }

    // Helper: check if a candidate Y for a new item is at least minDist from all other active items
    private boolean isItemYValid(int candidateY, int candidateH, int[] otherYs, int[] otherHs, int minDist) {
        for (int i = 0; i < otherYs.length; i++) {
            if (!isVerticallyApart(candidateY, candidateH, otherYs[i], otherHs[i], minDist)) {
                return false;
            }
        }
        return true;
    }

    // Deterministic suplemen spawn: 1 per 250m, always at phaseStart + 125m (150m after first meat)
    private void spawnSuplemenIfNeeded() {
        int phaseStart = suplemenPhase * 250;
        int phaseEnd = (suplemenPhase + 1) * 250;
        int suplemenSpawnScore = phaseStart + 125; // 125m after phase start (first meat at 50m, suplemen at 200m)
        if (score >= suplemenSpawnScore && score < phaseEnd && suplemenAppearCount < 1 && !suplemenActive) {
            suplemenActive = true;
            suplemenX = 1280 - suplemenW - 10;
            int jumpHeight = 225;
            int marginAbovePlatform = 60;
            int minY = groundY - suplemenH - jumpHeight;
            int maxY = groundY - suplemenH - marginAbovePlatform;
            if (minY < 0) minY = 0;
            if (maxY < minY) maxY = minY + 1;
            // Try up to 20 times to find a Y that doesn't overlap with fish or meat
            int[] otherYs = new int[2];
            int[] otherHs = new int[2];
            int idx = 0;
            if (fishActive) { otherYs[idx] = fishY; otherHs[idx] = fishH; idx++; }
            if (meatActive) { otherYs[idx] = meatY; otherHs[idx] = meatH; idx++; }
            int validY = minY;
            for (int attempt = 0; attempt < 20; attempt++) {
                int candidateY = minY + (int)(Math.random() * (maxY - minY + 1));
                if (isItemYValid(candidateY, suplemenH, java.util.Arrays.copyOf(otherYs, idx), java.util.Arrays.copyOf(otherHs, idx), 10)) {
                    validY = candidateY;
                    break;
                }
            }
            suplemenY = validY;
            suplemenAppearCount++;
            lastSuplemenSpawnScore = score;
        }
    }
    // Track last meat spawn position (in meters)
    private int lastMeatSpawnScore = -1000;
    // Meat item
    private Image meatImg = new ImageIcon("images/meat.png").getImage();
    private int meatX = 0, meatY = 0, meatW = 48, meatH = 48;
    private boolean meatActive = false;
    private int meatTakenThisPhase = 0;
    private int meatPhase = 0;
    private int meatAppearCount = 0;
    private long meatEffectStartTime = 0;
    private boolean meatEffectActive = false;
    private final int meatEffectDurationMs = 3000; // 3 seconds
    // Attack animation
    private Image attackSheet = new ImageIcon("images/Attack.png").getImage();
    private Image[] attackFrames = new Image[4];
    private int attackFrame = 0;
    private int attackFrameTick = 0;
    private final int attackFrameDelay = 4; // Animation speed
    private boolean isAttacking = false;
    private int attackAnimTicks = 0;
    private final int attackAnimDuration = 16; // duration in ticks (frames)
    // Platform movement
    private double platformX = 0;
    private BufferedImage platformImg = null;
    private int platformImgW = 0;
    private int platformImgH = 0;
    // Fish item for level up
    private Image fishImg = new ImageIcon("images/fish.png").getImage();
    private int fishCollected = 0;
    private int fishNeeded = 15;
    private boolean fishActive = false;
    private int fishX = 0, fishY = 0, fishW = 48, fishH = 48;
    private boolean levelUpRequired = false;
    private boolean levelUpFailed = false;
    private int fishPhase = 0; // 0: first phase, increments every 250m
    // (Pillar variables removed)
    // Moving obstacle (vertical)
    private boolean obsMoving = false;
    private int obsMoveDir = 1; // 1 = down, -1 = up
    private int obsMoveSpeed = 4;
    private int obsMoveMinY, obsMoveMaxY;
    // Bird animation for floating obstacle
    private Image birdSheet = new ImageIcon("images/bird.png").getImage();
    private Image[] birdFrames = new Image[6];
    private int birdFrame = 0;
    private int birdFrameTick = 0;
    private final int birdFrameDelay = 3; // Lower = faster animation
    private JDialog gameOverDialog = null;
    // Hurt animation
    private Image hurtSheet = new ImageIcon("images/Hurt.png").getImage();
    private Image[] hurtFrames = new Image[2];
    private int hurtFrame = 0;
    private int hurtFrameTick = 0;
    private final int hurtFrameDelay = 6;
    private boolean isHurting = false;
    private int hurtAnimTicks = 0;
    private final int hurtAnimDuration = 18; // duration in ticks (frames)
    // Character animation
    private Image walkSheet = new ImageIcon("images/Walk.png").getImage();
    private Image[] walkFrames = new Image[6];
    private int walkFrame = 0;
    private int walkFrameTick = 0;
    private final int walkFrameDelay = 3; // Lower = faster animation (was 5)
    // Background scrolling
    private double bgX = 0;
    private Image dayBg = new ImageIcon("images/day.png").getImage();
    private Image nightBg = new ImageIcon("images/night.png").getImage();
    private boolean isDay = true;
private int health = 4;
private final int maxHealth = 4;
    private Timer timer;
    private int playerY = 360, playerVelY = 0;
    private final int playerX = 200, playerW = 50, playerH = 50;
    private boolean jumping = false;
    private int groundY = 600; // Put platform and character back to original position
    private double obsX;
    private int obsW;
    private int obsH;
    private int obsY;
    private boolean obsFloating = false;
    private int speed = 12; // Lowered from 20 for slower movement
    private double speedMultiplier = 1.0;
    private long startTime;
    private int score = 0; // now used for meters
    private double accumulatedDistance = 0.0;
    private int currentLevel = 1;
    private boolean levelUpMessage = false;
    private long levelUpMessageTime = 0;
    private int highScore = 0;
    private boolean running = true;
    private final String HIGHSCORE_FILE = "highscore.txt";

    public GamePanel() {
        // Slice Death.png into 4 frames for death animation
        int deathSheetW = deathSheet.getWidth(this);
        int deathSheetH = deathSheet.getHeight(this);
        if (deathSheetW > 0 && deathSheetH > 0) {
            int frameW = deathSheetW / 4;
            for (int i = 0; i < 4; i++) {
                deathFrames[i] = new BufferedImage(frameW, deathSheetH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = ((BufferedImage)deathFrames[i]).createGraphics();
                g2.drawImage(deathSheet, 0, 0, frameW, deathSheetH, i * frameW, 0, (i + 1) * frameW, deathSheetH, this);
                g2.dispose();
            }
        }
        // Place first meat if needed
        spawnMeatIfNeeded();
        // Slice Attack.png into 4 frames for attack animation
        int attackSheetW = attackSheet.getWidth(this);
        int attackSheetH = attackSheet.getHeight(this);
        if (attackSheetW > 0 && attackSheetH > 0) {
            int frameW = attackSheetW / 4;
            for (int i = 0; i < 4; i++) {
                attackFrames[i] = new BufferedImage(frameW, attackSheetH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = ((BufferedImage)attackFrames[i]).createGraphics();
                g2.drawImage(attackSheet, 0, 0, frameW, attackSheetH, i * frameW, 0, (i + 1) * frameW, attackSheetH, this);
                g2.dispose();
            }
        }
        // (Pillar image loading removed)
        // Place first fish if needed
        spawnFishIfNeeded();
        // Slice bird.png into 6 frames for bird animation
        int birdSheetW = birdSheet.getWidth(this);
        int birdSheetH = birdSheet.getHeight(this);
        if (birdSheetW > 0 && birdSheetH > 0) {
            int frameW = birdSheetW / 6;
            for (int i = 0; i < 6; i++) {
                birdFrames[i] = new BufferedImage(frameW, birdSheetH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = ((BufferedImage)birdFrames[i]).createGraphics();
                g2.drawImage(birdSheet, 0, 0, frameW, birdSheetH, i * frameW, 0, (i + 1) * frameW, birdSheetH, this);
                g2.dispose();
            }
        }
        // Slice Hurt.png into 2 frames
        int hurtSheetW = hurtSheet.getWidth(this);
        int hurtSheetH = hurtSheet.getHeight(this);
        if (hurtSheetW > 0 && hurtSheetH > 0) {
            int frameW = hurtSheetW / 2;
            for (int i = 0; i < 2; i++) {
                hurtFrames[i] = new BufferedImage(frameW, hurtSheetH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = ((BufferedImage)hurtFrames[i]).createGraphics();
                g2.drawImage(hurtSheet, 0, 0, frameW, hurtSheetH, i * frameW, 0, (i + 1) * frameW, hurtSheetH, this);
                g2.dispose();
            }
        }
        // Slice walk.png into 6 frames
        int sheetW = walkSheet.getWidth(this);
        int sheetH = walkSheet.getHeight(this);
        if (sheetW > 0 && sheetH > 0) {
            int frameW = sheetW / 6;
            for (int i = 0; i < 6; i++) {
                walkFrames[i] = new BufferedImage(frameW, sheetH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = ((BufferedImage)walkFrames[i]).createGraphics();
                g2.drawImage(walkSheet, 0, 0, frameW, sheetH, i * frameW, 0, (i + 1) * frameW, sheetH, this);
                g2.dispose();
            }
        }
        // Load platform image once
        try {
            platformImg = javax.imageio.ImageIO.read(new File("images/platform.png"));
            platformImgW = platformImg.getWidth();
            platformImgH = platformImg.getHeight();
        } catch (Exception ex) {
            platformImg = null;
            platformImgW = 0;
            platformImgH = 0;
        }
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        timer = new Timer(16, this);
        initObstacle();
        loadHighScore();
        startTime = System.currentTimeMillis();
        accumulatedDistance = 0.0;
        timer.start();
    }

    private void initObstacle() {
        // If level up is required, don't spawn obstacles
        if (levelUpRequired) return;
        obsX = 1280;
        // Floating obstacles after 250m, moving obstacles after 500m
        boolean allowFloating = score >= 250;
        boolean allowMoving = score >= 500;
        double floatChance = allowFloating ? 0.3 : 0.0;
        double movingChance = allowMoving ? 0.2 : 0.0;
        double r = Math.random();
        if (r < floatChance) {
            // Floating obstacle: always same height and always floats at a fixed height from the ground
            obsFloating = true;
            obsMoving = false;
            obsW = 80;
            obsH = 80;
            int floatGap = 180;
            obsY = groundY - obsH - floatGap;
        } else if (r < floatChance + movingChance) {
            // Moving obstacle: moves up and down
            obsFloating = false;
            obsMoving = true;
            obsW = 80;
            obsH = 80;
            // Wider vertical range: from much higher to much lower
            obsMoveMinY = groundY - 350;
            obsMoveMaxY = groundY - 30;
            // Clamp so the obstacle doesn't go off screen
            if (obsMoveMinY < 0) obsMoveMinY = 0;
            if (obsMoveMaxY > groundY - obsH) obsMoveMaxY = groundY - obsH;
            // Start in the middle of the allowed range
            obsY = obsMoveMaxY - (obsMoveMaxY - obsMoveMinY) / 2;
            obsMoveDir = Math.random() < 0.5 ? 1 : -1;
        } else {
            obsFloating = false;
            obsMoving = false;
            obsW = 80;
            obsH = 80;
            obsY = groundY - obsH;
        }
    }

    private void loadHighScore() {


        try (BufferedReader br = new BufferedReader(new FileReader(HIGHSCORE_FILE))) {
            highScore = Integer.parseInt(br.readLine());
        } catch (Exception e) {
            highScore = 0;
        }
    }

    // Spawn a fish if needed for level up, ensuring no vertical overlap with meat or suplemen (min 10px)
    private void spawnFishIfNeeded() {
        int phaseStart = fishPhase * 250;
        int phaseEnd = (fishPhase + 1) * 250;
        if (score >= phaseStart && score < phaseEnd) {
            fishActive = true;
            fishX = 1280 - fishW - 10;
            int jumpHeight = 225;
            int marginAbovePlatform = 10;
            int minY = groundY - fishH - jumpHeight;
            int maxY = groundY - fishH - marginAbovePlatform;
            if (minY < 0) minY = 0;
            if (maxY < minY) maxY = minY + 1;
            // Try up to 20 times to find a Y that doesn't overlap with meat or suplemen
            int[] otherYs = new int[2];
            int[] otherHs = new int[2];
            int idx = 0;
            if (meatActive) { otherYs[idx] = meatY; otherHs[idx] = meatH; idx++; }
            if (suplemenActive) { otherYs[idx] = suplemenY; otherHs[idx] = suplemenH; idx++; }
            int validY = minY;
            boolean found = false;
            for (int attempt = 0; attempt < 20; attempt++) {
                int candidateY = minY + (int)(Math.random() * (maxY - minY + 1));
                if (isItemYValid(candidateY, fishH, java.util.Arrays.copyOf(otherYs, idx), java.util.Arrays.copyOf(otherHs, idx), 10)) {
                    validY = candidateY;
                    found = true;
                    break;
                }
            }
            fishY = validY;
        } else {
            fishActive = false;
        }
    }

    // Deterministic meat spawn: 2 per 250m, first at phaseStart+50, second at phaseStart+200 (75 after suplemen)
    private void spawnMeatIfNeeded() {
        int phaseStart = meatPhase * 250;
        int phaseEnd = (meatPhase + 1) * 250;
        int[] meatSpawnScores = new int[] { phaseStart + 50, phaseStart + 200 };
        // Only spawn if not already active and not already spawned at this slot
        for (int i = 0; i < 2; i++) {
            if (meatAppearCount == i && !meatActive && score >= meatSpawnScores[i] && score < phaseEnd) {
                meatActive = true;
                meatX = 1280 - meatW - 10;
                int jumpHeight = 225;
                int marginAbovePlatform = 60;
                int minY = groundY - meatH - jumpHeight;
                int maxY = groundY - meatH - marginAbovePlatform;
                if (minY < 0) minY = 0;
                if (maxY < minY) maxY = minY + 1;
                // Try up to 20 times to find a Y that doesn't overlap with fish or suplemen
                int[] otherYs = new int[2];
                int[] otherHs = new int[2];
                int idx = 0;
                if (fishActive) { otherYs[idx] = fishY; otherHs[idx] = fishH; idx++; }
                if (suplemenActive) { otherYs[idx] = suplemenY; otherHs[idx] = suplemenH; idx++; }
                int validY = minY;
                for (int attempt = 0; attempt < 20; attempt++) {
                    int candidateY = minY + (int)(Math.random() * (maxY - minY + 1));
                    if (isItemYValid(candidateY, meatH, java.util.Arrays.copyOf(otherYs, idx), java.util.Arrays.copyOf(otherHs, idx), 10)) {
                        validY = candidateY;
                        break;
                    }
                }
                meatY = validY;
                meatAppearCount++;
                lastMeatSpawnScore = score;
                break;
            }
        }
        // Do not forcibly set meatActive = false here; let it be controlled by collection or going off screen
    }

    private void saveHighScore() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(HIGHSCORE_FILE))) {
            bw.write(String.valueOf(highScore));
        } catch (Exception ignored) {}
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Game always runs, no menu blocking
        // If dying, only advance death animation, then show game over dialog
        if (isDying) {
            deathFrameTick++;
            if (deathFrameTick >= deathFrameDelay) {
                deathFrame++;
                deathFrameTick = 0;
                if (deathFrame >= deathFrames.length) {
                    deathFrame = deathFrames.length - 1;
                }
            }
            deathAnimTicks++;
            if (deathAnimTicks >= deathAnimDuration) {
                // Do not reset isDying or set deathAnimDone here!
                // Just show the game over dialog and stop the timer, so the character stays dead
                Timer popupDelay = new Timer(100, evt -> {
                    ((Timer)evt.getSource()).stop();
                    timer.stop();
                    showGameOverDialog();
                });
                popupDelay.setRepeats(false);
                popupDelay.start();
            }
            repaint();
            return;
        }
        // Handle suplemen phase and respawn logic
        int suplemenPhaseEnd = (suplemenPhase + 1) * 250;
        if (score >= suplemenPhaseEnd) {
            suplemenPhase++;
            suplemenAppearCount = 0;
            suplemenActive = false;
            suplemenEffectActive = false;
            suplemenEffectUsed = false;
        }

        // Always call spawnSuplemenIfNeeded every frame
        spawnSuplemenIfNeeded();

        // Move suplemen if active
        if (suplemenActive) {
            suplemenX -= (int)(speed * speedMultiplier);
            if (suplemenX + suplemenW < 0) {
                suplemenActive = false;
            }
        }

        // Check for suplemen collection
        if (suplemenActive && running) {
            Rectangle playerRect = new Rectangle(playerX, playerY, playerW, playerH);
            Rectangle suplemenRect = new Rectangle(suplemenX, suplemenY, suplemenW, suplemenH);
            if (playerRect.intersects(suplemenRect)) {
                suplemenActive = false;
                suplemenEffectActive = true;
                suplemenEffectUsed = false;
                // Trigger attack animation when collecting suplemen
                isAttacking = true;
                attackAnimTicks = 0;
                attackFrame = 0;
                attackFrameTick = 0;
            }
        }
        // Handle meat effect duration
        if (meatEffectActive && System.currentTimeMillis() - meatEffectStartTime > meatEffectDurationMs) {
            meatEffectActive = false;
        }

        // Handle meat phase and respawn logic
        int meatPhaseEnd = (meatPhase + 1) * 250;
        if (score >= meatPhaseEnd) {
            meatPhase++;
            meatAppearCount = 0;
            lastMeatSpawnScore = -1000;
            meatActive = false;
        }

        // Always call spawnMeatIfNeeded every frame to allow up to 2 spawns per phase
        spawnMeatIfNeeded();

        // Move meat if active (use same speed as obstacle)
        if (meatActive) {
            meatX -= (int)(speed * speedMultiplier);
            // If meat goes off screen, deactivate but do not increment meatAppearCount (already incremented on spawn)
            if (meatX + meatW < 0) {
                meatActive = false;
            }
        }

        // Check for meat collection
        if (meatActive && running) {
            Rectangle playerRect = new Rectangle(playerX, playerY, playerW, playerH);
            Rectangle meatRect = new Rectangle(meatX, meatY, meatW, meatH);
            if (playerRect.intersects(meatRect)) {
                meatActive = false;
                meatEffectActive = true;
                meatEffectStartTime = System.currentTimeMillis();
                // Trigger attack animation when collecting meat
                isAttacking = true;
                attackAnimTicks = 0;
                attackFrame = 0;
                attackFrameTick = 0;
            }
        }
        // Attack animation logic
        if (isAttacking) {
            attackFrameTick++;
            if (attackFrameTick >= attackFrameDelay) {
                attackFrame = (attackFrame + 1) % 4;
                attackFrameTick = 0;
            }
            attackAnimTicks++;
            if (attackAnimTicks >= attackAnimDuration) {
                isAttacking = false;
                attackAnimTicks = 0;
                attackFrame = 0;
            }
        }
        // Move platform to the right at the same speed as obstacle
        if (platformImgW > 0) {
            platformX += speed * speedMultiplier;
            if (platformX >= platformImgW) platformX -= platformImgW;
        } else {
            platformX += speed * speedMultiplier;
            if (platformX >= 1280) platformX -= 1280;
        }
        // Handle fish collection phases and level up/fail logic (endless)
        int phaseEnd = (fishPhase + 1) * 250;
        if (score >= phaseEnd) {
            levelUpRequired = true;
            if (fishCollected < fishNeeded) {
                // Not enough fish, fail
                levelUpFailed = true;
                health = 0;
                running = false;
                // Prevent level up message from showing if failed
                levelUpMessage = false;
                timer.stop();
                repaint();
                Timer popupDelay = new Timer(20, evt -> {
                    ((Timer)evt.getSource()).stop();
                    showGameOverDialog();
                });
                popupDelay.setRepeats(false);
                popupDelay.start();
                return;
            } else {
                // Enough fish, continue to next phase
                levelUpRequired = false;
                fishActive = false;
                fishPhase++;
                fishCollected = 0;
                fishNeeded = 15;
                spawnFishIfNeeded(); // Ensure fish appears at start of next phase
            }
        }

        // Move fish if active (use same speed as obstacle)
        if (fishActive) {
            fishX -= (int)(speed * speedMultiplier);
            // If fish goes off screen, respawn
            if (fishX + fishW < 0) {
                spawnFishIfNeeded();
            }
        }
        // Check for fish collection
        if (fishActive && running) {
            Rectangle playerRect = new Rectangle(playerX, playerY, playerW, playerH);
            Rectangle fishRect = new Rectangle(fishX, fishY, fishW, fishH);
            if (playerRect.intersects(fishRect)) {
                fishCollected++;
                spawnFishIfNeeded();
                // Trigger attack animation
                isAttacking = true;
                attackAnimTicks = 0;
                attackFrame = 0;
                attackFrameTick = 0;
            }
        }
        // Move the moving obstacle up and down
        if (obsMoving) {
            obsY += obsMoveDir * obsMoveSpeed;
            if (obsY <= obsMoveMinY) {
                obsY = obsMoveMinY;
                obsMoveDir = 1;
            } else if (obsY >= obsMoveMaxY) {
                obsY = obsMoveMaxY;
                obsMoveDir = -1;
            }
        }
        // Animate bird if floating obstacle is present
        if (obsFloating && birdFrames[0] != null) {
            birdFrameTick++;
            if (birdFrameTick >= birdFrameDelay) {
                birdFrame = (birdFrame + 1) % 6;
                birdFrameTick = 0;
            }
        }
        // Hurt animation logic
        if (isHurting) {
            hurtFrameTick++;
            if (hurtFrameTick >= hurtFrameDelay) {
                hurtFrame = (hurtFrame + 1) % 2;
                hurtFrameTick = 0;
            }
            hurtAnimTicks++;
            if (hurtAnimTicks >= hurtAnimDuration) {
                isHurting = false;
                hurtAnimTicks = 0;
                hurtFrame = 0;
            }
        }
        // Animate character (only advance walk animation if on ground and not jumping or hurting)
        if (!isHurting && !jumping && playerY >= groundY - playerH) {
            walkFrameTick++;
            if (walkFrameTick >= walkFrameDelay) {
                walkFrame = (walkFrame + 1) % 6;
                walkFrameTick = 0;
            }
        }
        // Move background at 0.1x platform speed (classic parallax)
        double bgSpeed = speed * speedMultiplier * 0.025;
        bgX -= bgSpeed;
        // Loop background
        if (bgX <= -1280) bgX += 1280;

        // Day/night switch every 250 meters
        boolean newIsDay = ((score / 250) % 2 == 0);
        if (newIsDay != isDay) {
            isDay = newIsDay;
        }
        if (!running) return;
        // Increase speed every 250 meters (use score for exact sync with background change)
        speedMultiplier = 1.0 + 0.2 * (score / 250);
        // Move obstacle and item at the same speed (speedMultiplier applies to both)
        int gap = 170; // Lowered gap for more frequent obstacles (was 250)
        obsX -= (speed * speedMultiplier);
        if (obsX + obsW < 0) {
            // Place next obstacle closer (less gap)
            obsX += 1280 + gap;
            initObstacle();
        }
        // Gravity
        playerVelY += 2;
        playerY += playerVelY;
        if (playerY >= groundY - playerH) {
            playerY = groundY - playerH;
            playerVelY = 0;
            jumping = false;
        }
        // Collision
        Rectangle playerRect = new Rectangle(playerX, playerY, playerW, playerH);
        Rectangle obsRect = new Rectangle((int)obsX, obsY, obsW, obsH);
        if (playerRect.intersects(obsRect)) {
            if (suplemenEffectActive && !suplemenEffectUsed) {
                // Resist obstacle, do not decrease health, consume effect
                suplemenEffectUsed = true;
                suplemenEffectActive = false;
                initObstacle();
            } else {
                if (!isHurting) {
                    isHurting = true;
                    hurtAnimTicks = 0;
                    hurtFrame = 0;
                    hurtFrameTick = 0;
                }
                if (health > 0) {
                    health--;
                    initObstacle();
                }
                if (health <= 0 && !isDying && !deathAnimDone) {
                    running = false;
                    if (score > highScore) {
                        highScore = score;
                        saveHighScore();
                    }
                    // Start death animation
                    health = 0;
                    isDying = true;
                    deathAnimTicks = 0;
                    deathFrame = 0;
                    deathFrameTick = 0;
                    repaint();
                    return;
                }
            }
        }

        // --- LEVEL SYSTEM ---
        // Accumulate distance per frame using only the base speed (not affected by multiplier)
        double distanceThisFrame = (speed / 50.0) * 0.5;
        accumulatedDistance += distanceThisFrame;
        score = (int)Math.floor(accumulatedDistance); // score is now meters
        int newLevel = (int)(accumulatedDistance / 250.0) + 1;
        if (newLevel > currentLevel) {
            currentLevel = newLevel;
            levelUpMessage = true;
            levelUpMessageTime = System.currentTimeMillis();
        }
        // Speed increases every level (every 250 meters)
        speedMultiplier = 1.0 + 0.2 * (currentLevel - 1);
        repaint();
    }

    // Show a custom game over dialog with retry/close and colored text
    private void showGameOverDialog() {
        if (gameOverDialog != null && gameOverDialog.isVisible()) return;
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
        gameOverDialog = new JDialog(parent, "Game Over", true);
        gameOverDialog.setSize(420, 300);
        gameOverDialog.setLocationRelativeTo(this);
        gameOverDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        gameOverDialog.setResizable(false);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // Custom background
                g.setColor(new Color(30, 30, 60));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setLayout(null);

        JLabel title = new JLabel("GAME OVER", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 40));
        title.setForeground(new Color(220, 40, 40));
        title.setBounds(0, 20, 420, 50);
        panel.add(title);


        JLabel scoreLabel = new JLabel("Score: " + score + " m", SwingConstants.CENTER);
        scoreLabel.setFont(new Font("Arial", Font.BOLD, 28));
        scoreLabel.setForeground(new Color(255, 215, 0));
        scoreLabel.setBounds(0, 70, 420, 40);
        panel.add(scoreLabel);

        JLabel levelLabel = new JLabel("Level: " + currentLevel, SwingConstants.CENTER);
        levelLabel.setFont(new Font("Arial", Font.BOLD, 24));
        levelLabel.setForeground(new Color(120, 255, 255));
        levelLabel.setBounds(0, 110, 420, 32);
        panel.add(levelLabel);

        JLabel highScoreLabel = new JLabel("High Score: " + highScore + " m", SwingConstants.CENTER);
        highScoreLabel.setFont(new Font("Arial", Font.BOLD, 22));
        highScoreLabel.setForeground(new Color(100, 255, 100));
        highScoreLabel.setBounds(0, 150, 420, 30);
        panel.add(highScoreLabel);

        JButton retryBtn = new JButton("Retry");
        retryBtn.setFont(new Font("Arial", Font.BOLD, 22));
        retryBtn.setBackground(new Color(60, 180, 255));
        retryBtn.setForeground(Color.WHITE);
        retryBtn.setFocusPainted(false);
        retryBtn.setBounds(60, 180, 120, 50);
        retryBtn.addActionListener(e -> {
            gameOverDialog.dispose();
            restartGame();
        });
        panel.add(retryBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Arial", Font.BOLD, 22));
        closeBtn.setBackground(new Color(220, 40, 40));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBounds(240, 180, 120, 50);
        closeBtn.addActionListener(e -> {
            System.exit(0);
        });
        panel.add(closeBtn);

        gameOverDialog.setContentPane(panel);
        gameOverDialog.setVisible(true);
    }

    // Restart the game from the dialog
    private void restartGame() {
        isDying = false;
        deathAnimDone = false;
        deathAnimTicks = 0;
        deathFrame = 0;
        deathFrameTick = 0;
        fishCollected = 0;
        fishActive = false;
        fishNeeded = 15;
        levelUpRequired = false;
        levelUpFailed = false;
        fishPhase = 0;
        meatActive = false;
        meatAppearCount = 0;
        meatPhase = 0;
        lastMeatSpawnScore = -1000;
        meatEffectActive = false;
        meatEffectStartTime = 0;
        suplemenActive = false;
        suplemenAppearCount = 0;
        suplemenPhase = 0;
        suplemenEffectActive = false;
        suplemenEffectUsed = false;
        playerY = 360;
        playerVelY = 0;
        jumping = false;
        initObstacle();
        accumulatedDistance = 0.0;
        score = 0;
        health = maxHealth;
        running = true;
        isHurting = false;
        hurtAnimTicks = 0;
        hurtFrame = 0;
        hurtFrameTick = 0;
        walkFrame = 0;
        walkFrameTick = 0;
        speedMultiplier = 1.0;
        currentLevel = 1;
        levelUpMessage = false;
        levelUpMessageTime = 0;
        // Force fish to spawn immediately after retry (phase 0, score 0)
        spawnFishIfNeeded();
        timer.restart();
        requestFocusInWindow();
    }

    // (KeyListener methods already implemented above)

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Draw background (scrolling, seamless)
        Image bgImg = isDay ? dayBg : nightBg;
        int bgWidth = 1280, bgHeight = 720;
        int bgXInt = (int) bgX;
        g.drawImage(bgImg, bgXInt, 0, bgWidth, bgHeight, this);
        // Draw second image for seamless loop
        if (bgXInt + bgWidth < 1280) {
            g.drawImage(bgImg, bgXInt + bgWidth, 0, bgWidth, bgHeight, this);
        }
        // Draw suplemen if active (always in front of background)
        if (suplemenActive) {
            g.drawImage(suplemenImg, suplemenX, suplemenY, suplemenW, suplemenH, this);
        }
        // Draw meat if active (always in front of background)
        if (meatActive) {
            g.drawImage(meatImg, meatX, meatY, meatW, meatH, this);
        }
        // Draw fish if active (always in front of background)
        if (fishActive) {
            g.drawImage(fishImg, fishX, fishY, fishW, fishH, this);
        }

        // (Pillar drawing removed)
        // (Powerup drawing removed)

        // Draw health bar using health.png icons, right side, text and bar centered as a group
        int barWidth = maxHealth * 48;
        int barHeight = 60;
        int y = 40;
        int rightMargin = 50; // match score box
        int healthBoxPadding = 30; // padding from top, like score box
        int groupWidth = Math.max(barWidth, 180); // 180 is min width for text centering
        int groupX = 1280 - groupWidth - rightMargin;
        int groupY = healthBoxPadding;
        Graphics2D g2dHealth = (Graphics2D) g.create();
        // Draw black transparent background (no border)
        g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2dHealth.setColor(new Color(30, 30, 40));
        g2dHealth.fillRoundRect(groupX - 16, groupY - 12, groupWidth + 32, barHeight + 44, 28, 28);
        g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        // Draw label above health bar, centered within group
        g2dHealth.setFont(new Font("Arial", Font.BOLD, 20));
        g2dHealth.setColor(Color.WHITE);
        String healthLabel = "Remaining Health";
        FontMetrics fm = g2dHealth.getFontMetrics();
        int labelWidth = fm.stringWidth(healthLabel);
        int labelX = groupX + (groupWidth - labelWidth) / 2;
        int labelY = groupY + 18;
        g2dHealth.drawString(healthLabel, labelX, labelY);
        // Draw hearts (health.png for full, faded for missing), centered within group
        Image heartImg = new ImageIcon("images/health.png").getImage();
        int heartsStartX = groupX + (groupWidth - barWidth) / 2;
        int heartsY = labelY + 8;
        for (int i = 0; i < maxHealth; i++) {
            int heartX = heartsStartX + i * 48;
            int heartY = heartsY;
            if (i < health) {
                g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            } else {
                g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            }
            g2dHealth.drawImage(heartImg, heartX, heartY, 40, 40, this);
        }
        g2dHealth.dispose();

        // Draw moving ground/platform image at the bottom of the window, smooth seamless loop
        if (platformImg != null && platformImgW > 0 && platformImgH > 0) {
            int drawY = 720 - platformImgH;
            int px = (int)Math.round(platformX) % platformImgW;
            if (px < 0) px += platformImgW;
            // Draw enough tiles to cover the screen, always one extra
            for (int x = -px; x < 1280 + platformImgW; x += platformImgW) {
                g.drawImage(platformImg, x, drawY, platformImgW, platformImgH, this);
            }
        } else {
            // fallback: solid color at the bottom
            g.setColor(Color.DARK_GRAY);
            g.fillRect(0, 650, 1280, 70);
        }
        // Draw player (animated, bigger sprite but same hitbox)
        int spriteW = (int)(playerW * (meatEffectActive ? 4.2 : 3));
        int spriteH = (int)(playerH * (meatEffectActive ? 4.2 : 3));
        int spriteX = playerX - (spriteW - playerW) / 2;
        int spriteY = playerY + playerH - spriteH;
        boolean shine = suplemenEffectActive && !suplemenEffectUsed;
        Graphics2D g2dSprite = (Graphics2D) g.create();
        if (shine && shineImg != null) {
            g2dSprite.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            g2dSprite.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2dSprite.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // Draw shine image behind the sprite, centered and scaled (even smaller size, more up)
            int shineW = (int)(spriteW * 0.45); // smaller
            int shineH = (int)(spriteH * 0.45); // smaller
            int shineX = spriteX + (spriteW - shineW) / 2;
            int shineY = spriteY + (int)(spriteH * 0.52) - (shineH / 2); // still under body, but more up
            g2dSprite.drawImage(shineImg, shineX, shineY, shineW, shineH, this);
        }
        if (isDying && deathFrames[0] != null) {
            g2dSprite.drawImage(deathFrames[deathFrame], spriteX, spriteY, spriteW, spriteH, this);
        } else if (isAttacking && attackFrames[0] != null) {
            g2dSprite.drawImage(attackFrames[attackFrame], spriteX, spriteY, spriteW, spriteH, this);
        } else if (isHurting && hurtFrames[0] != null) {
            g2dSprite.drawImage(hurtFrames[hurtFrame], spriteX, spriteY, spriteW, spriteH, this);
        } else if (walkFrames[0] != null) {
            g2dSprite.drawImage(walkFrames[walkFrame], spriteX, spriteY, spriteW, spriteH, this);
        } else {
            g2dSprite.setColor(Color.CYAN);
            g2dSprite.fillRect(playerX, playerY, playerW, playerH);
        }
        // No white overlay, shine is now image-based
        g2dSprite.dispose();
        g2dSprite.dispose();
        // Draw obstacle: bush (ground), animated bird (floating), or moving obstacle (crystal)
        if (obsMoving) {
            // Use a different image for moving obstacle, e.g., images/ground.png as a placeholder (replace as needed)
            Image obsImg = new ImageIcon("images/ground.png").getImage();
            g.drawImage(obsImg, (int)Math.round(obsX), obsY, obsW, obsH, this);
        } else if (!obsFloating) {
            Image obsImg = new ImageIcon("images/bush.png").getImage();
            g.drawImage(obsImg, (int)Math.round(obsX), obsY, obsW, obsH, this);
        } else {
            if (birdFrames[0] != null) {
                // Draw the bird frame scaled up to 1.5x the obstacle box, but keep collision the same
                int frameW = birdFrames[birdFrame].getWidth(this);
                int frameH = birdFrames[birdFrame].getHeight(this);
                int scaleW = (int)(obsW * 1.5);
                int scaleH = (int)(obsH * 1.5);
                int drawX = (int)Math.round(obsX) + (obsW - scaleW) / 2;
                int drawY = obsY + (obsH - scaleH) / 2;
                g.drawImage(birdFrames[birdFrame], drawX, drawY, scaleW, scaleH, this);
            } else {
                // fallback: draw static bird.png, also scaled up
                Image obsImg = new ImageIcon("images/bird.png").getImage();
                int scaleW = (int)(obsW * 1.5);
                int scaleH = (int)(obsH * 1.5);
                int drawX = (int)Math.round(obsX) + (obsW - scaleW) / 2;
                int drawY = obsY + (obsH - scaleH) / 2;
                g.drawImage(obsImg, drawX, drawY, scaleW, scaleH, this);
            }
        }
        // Draw score and info with black transparent background (no border)
        int scoreBoxX = 20;
        int scoreBoxY = 20;
        int scoreBoxW = 340;
        int scoreBoxH = 170;
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
        g2d.setColor(new Color(20, 20, 30));
        g2d.fillRoundRect(scoreBoxX, scoreBoxY, scoreBoxW, scoreBoxH, 28, 28);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        int scoreTextX = scoreBoxX + 18;
        int scoreTextY = scoreBoxY + 34;
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.setColor(Color.WHITE);
        g2d.drawString("Score: " + score + " m", scoreTextX, scoreTextY);

        // Draw level text
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.setColor(new Color(255, 215, 0));
        g2d.drawString("Level: " + currentLevel, scoreTextX, scoreTextY + 30);

        // Draw level up message if just leveled up (keep background for visibility)
        if (levelUpMessage && System.currentTimeMillis() - levelUpMessageTime < 2000) {
            String levelUpMsg = "selamat anda naik level " + currentLevel + "!";
            g2d.setFont(new Font("Arial", Font.BOLD, 36));
            g2d.setColor(new Color(255, 215, 0));
            FontMetrics fmLevelUp = g2d.getFontMetrics();
            int msgWidthLevelUp = fmLevelUp.stringWidth(levelUpMsg);
            int msgHeightLevelUp = fmLevelUp.getHeight();
            int xLevelUp = (getWidth() - msgWidthLevelUp) / 2;
            int yLevelUp = (getHeight() - msgHeightLevelUp) / 2 + fmLevelUp.getAscent();
            // Draw a semi-transparent rounded rectangle background for visibility
            int padX = 32, padY = 18;
            int bgX = xLevelUp - padX;
            int bgY = yLevelUp - fmLevelUp.getAscent() - padY/2;
            int bgW = msgWidthLevelUp + padX * 2;
            int bgH = msgHeightLevelUp + padY;
            Composite oldComp = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
            g2d.setColor(new Color(30, 30, 30));
            g2d.fillRoundRect(bgX, bgY, bgW, bgH, 32, 32);
            g2d.setComposite(oldComp);
            g2d.setColor(new Color(255, 215, 0));
            g2d.drawString(levelUpMsg, xLevelUp, yLevelUp);
        } else {
            levelUpMessage = false;
        }

        // Draw speed multiplier text
        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        g2d.setColor(new Color(120, 255, 120));
        String speedText = String.format("Kecepatan: %.1fx", speedMultiplier);
        g2d.drawString(speedText, scoreTextX, scoreTextY + 88);

        // Draw fish collected counter below speed
        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        g2d.setColor(new Color(80, 200, 255));
        String fishText = String.format("Fish Collected: %d/%d", fishCollected, fishNeeded);
        g2d.drawString(fishText, scoreTextX, scoreTextY + 118);
        g2d.dispose();
        // Game over popup is now handled by dialog
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE && !jumping && running) {
            playerVelY = -30;
            jumping = true;
        }
        // R to restart is now handled by dialog only
    }
    @Override public void keyReleased(KeyEvent e) {}
    @Override public void keyTyped(KeyEvent e) {}
}
