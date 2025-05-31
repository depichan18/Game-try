import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.swing.*;



public class EndlessDashGame extends JFrame {
    public EndlessDashGame() {
        setTitle("Endless Dash Game");
        setSize(1280, 720);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        add(new GamePanel());
        setLocationRelativeTo(null);
        setVisible(true);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(EndlessDashGame::new);
    }
}


class GamePanel extends JPanel implements ActionListener, KeyListener {
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
    private int groundY = 600;
    private double obsX;
    private int obsW;
    private int obsH;
    private int obsY;
    private boolean obsFloating = false;
    private int speed = 12; // Lowered from 20 for slower movement
    private double speedMultiplier = 1.0;
    private long startTime;
    private int score = 0;
    private double accumulatedDistance = 0.0;
    private int highScore = 0;
    private boolean running = true;
    private final String HIGHSCORE_FILE = "highscore.txt";

    public GamePanel() {
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
        obsX = 1280;
        obsW = 80;
        // 40% chance to float (player should not jump)
        if (Math.random() < 0.4) {
            // Floating obstacle: always same height and always floats at a fixed height from the ground
            obsFloating = true;
            obsH = 80; // Fixed height for floating obstacles
            int floatGap = 180; // Distance from ground to bottom of floating obstacle
            obsY = groundY - obsH - floatGap;
        } else {
            obsFloating = false;
            obsH = 50 + (int)(Math.random() * 100);
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

    private void saveHighScore() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(HIGHSCORE_FILE))) {
            bw.write(String.valueOf(highScore));
        } catch (Exception ignored) {}
    }

    @Override
    public void actionPerformed(ActionEvent e) {
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
        double bgSpeed = speed * speedMultiplier * 0.25;
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
        // Move obstacle at normal platform speed
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
            if (health <= 0) {
                running = false;
                if (score > highScore) {
                    highScore = score;
                    saveHighScore();
                }
                // Render health bar at zero before showing dialog
                health = 0;
                repaint();
                Timer popupDelay = new Timer(20, evt -> {
                    ((Timer)evt.getSource()).stop();
                    timer.stop();
                    showGameOverDialog();
                });
                popupDelay.setRepeats(false);
                popupDelay.start();
                return;
            }
        }

        // --- FIXED SCORING SYSTEM ---
        // Accumulate distance per frame using only the base speed (not affected by multiplier)
        // 1 meter = 50 pixels, and speed is in pixels per frame
        // Make score speed half as before
        double distanceThisFrame = (speed / 50.0) * 0.5;
        accumulatedDistance += distanceThisFrame;
        score = (int)accumulatedDistance;
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
        scoreLabel.setBounds(0, 80, 420, 40);
        panel.add(scoreLabel);

        JLabel highScoreLabel = new JLabel("High Score: " + highScore + " m", SwingConstants.CENTER);
        highScoreLabel.setFont(new Font("Arial", Font.BOLD, 22));
        highScoreLabel.setForeground(new Color(100, 255, 100));
        highScoreLabel.setBounds(0, 120, 420, 30);
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
        // (Powerup reset removed)
        speedMultiplier = 1.0;
        timer.restart();
        requestFocusInWindow();
    }

    // (KeyListener methods already implemented above)

    @Override
    public void paintComponent(Graphics g) {
        // (Powerup drawing removed)
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

        // Draw health bar using health.png icons in a boxed section
        int barWidth = 220;
        int barHeight = 60;
        int startX = 1280 - barWidth - 30;
        int y = 40;
        // Draw health box background (rounded, semi-transparent)
        Graphics2D g2dHealth = (Graphics2D) g.create();
        g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
        g2dHealth.setColor(new Color(30, 30, 40));
        g2dHealth.fillRoundRect(startX - 16, y - 32, barWidth + 32, barHeight + 44, 28, 28);
        g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g2dHealth.setColor(new Color(80, 200, 255));
        g2dHealth.setStroke(new BasicStroke(3f));
        g2dHealth.drawRoundRect(startX - 16, y - 32, barWidth + 32, barHeight + 44, 28, 28);
        // Draw label above health bar, right-aligned
        g2dHealth.setFont(new Font("Arial", Font.BOLD, 20));
        g2dHealth.setColor(Color.WHITE);
        String healthLabel = "Remaining Health";
        FontMetrics fm = g2dHealth.getFontMetrics();
        int labelWidth = fm.stringWidth(healthLabel);
        g2dHealth.drawString(healthLabel, startX + barWidth - labelWidth, y - 10);
        // Draw hearts (health.png for full, faded for missing)
        Image heartImg = new ImageIcon("images/health.png").getImage();
        for (int i = 0; i < maxHealth; i++) {
            int heartX = startX + i * 48;
            int heartY = y + 10;
            if (i < health) {
                g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            } else {
                g2dHealth.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            }
            g2dHealth.drawImage(heartImg, heartX, heartY, 40, 40, this);
        }
        g2dHealth.dispose();

        // Draw ground
        g.setColor(Color.DARK_GRAY);
        g.fillRect(0, groundY, 1280, 720 - groundY);
        // Draw player (animated, bigger sprite but same hitbox)
        int spriteW = (int)(playerW * 3);
        int spriteH = (int)(playerH * 3);
        int spriteX = playerX - (spriteW - playerW) / 2;
        int spriteY = playerY + playerH - spriteH;
        if (isHurting && hurtFrames[0] != null) {
            g.drawImage(hurtFrames[hurtFrame], spriteX, spriteY, spriteW, spriteH, this);
        } else if (walkFrames[0] != null) {
            g.drawImage(walkFrames[walkFrame], spriteX, spriteY, spriteW, spriteH, this);
        } else {
            g.setColor(Color.CYAN);
            g.fillRect(playerX, playerY, playerW, playerH);
        }
        // Draw obstacle: ground = normal spike, floating = flipped spike with bar
        Graphics2D g2dObs = (Graphics2D) g.create();
        if (!obsFloating) {
            // Normal spike (triangle pointing up)
            int[] triX = { (int)Math.round(obsX), (int)Math.round(obsX) + obsW/2, (int)Math.round(obsX) + obsW };
            int[] triY = { obsY + obsH, obsY, obsY + obsH };
            g2dObs.setColor(Color.RED);
            g2dObs.fillPolygon(triX, triY, 3);
            g2dObs.setStroke(new BasicStroke(4f));
            g2dObs.setColor(Color.BLACK);
            g2dObs.drawPolygon(triX, triY, 3);
        } else {
            // Flipped spike (triangle pointing down) with bar from top of spike to top of screen
            int x0 = (int)Math.round(obsX);
            int x1 = x0 + obsW/2;
            int x2 = x0 + obsW;
            int y0 = obsY; // top of obstacle
            int y1 = obsY + obsH; // bottom of obstacle
            // Triangle pointing down
            int[] triX = { x0, x1, x2 };
            int[] triY = { y0, y1, y0 };
            g2dObs.setColor(Color.RED);
            g2dObs.fillPolygon(triX, triY, 3);
            g2dObs.setStroke(new BasicStroke(4f));
            g2dObs.setColor(Color.BLACK);
            g2dObs.drawPolygon(triX, triY, 3);
            // Draw bar from y1 (tip of spike) to top of screen
            g2dObs.setColor(new Color(80,80,80));
            g2dObs.setStroke(new BasicStroke(8f));
            g2dObs.drawLine(x1, y1, x1, 0);
        }
        g2dObs.dispose();
        // Draw score in a semi-transparent box for visibility
        int scoreBoxX = 20;
        int scoreBoxY = 20;
        int scoreBoxW = 340;
        int scoreBoxH = 130;
        // Draw background box (rounded, semi-transparent)
        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
        g2d.setColor(new Color(20, 20, 30));
        g2d.fillRoundRect(scoreBoxX, scoreBoxY, scoreBoxW, scoreBoxH, 28, 28);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        g2d.setColor(new Color(80, 200, 255));
        g2d.setStroke(new BasicStroke(3f));
        g2d.drawRoundRect(scoreBoxX, scoreBoxY, scoreBoxW, scoreBoxH, 28, 28);

        // Draw score text
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.setColor(Color.WHITE);
        g2d.drawString("Score: " + score + " m", scoreBoxX + 18, scoreBoxY + 44);
        g2d.setFont(new Font("Arial", Font.BOLD, 28));
        g2d.setColor(new Color(255, 215, 0));
        g2d.drawString("High Score: " + highScore + " m", scoreBoxX + 18, scoreBoxY + 80);

        // Draw speed multiplier text
        g2d.setFont(new Font("Arial", Font.BOLD, 22));
        g2d.setColor(new Color(120, 255, 120));
        String speedText = String.format("You are now at %.1fx speed", speedMultiplier);
        g2d.drawString(speedText, scoreBoxX + 18, scoreBoxY + 112);
        g2d.dispose();
        // Game over popup is now handled by dialog
    }

    // Draw a heart shape centered at (cx, cy) with given size, fill and outline color
    // (drawHeart method removed, now using images for health bar)

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
