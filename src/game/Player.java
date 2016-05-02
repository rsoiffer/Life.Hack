package game;

import engine.Core;
import engine.Input;
import engine.Signal;
import graphics.Graphics2D;
import graphics.Window3D;
import graphics.loading.SpriteContainer;
import map.CubeMap;
import static map.CubeMap.WORLD_SIZE;
import networking.Client;
import static networking.MessageType.SNOWBALL;
import org.lwjgl.input.Keyboard;
import static org.lwjgl.input.Keyboard.KEY_SPACE;
import static util.Color4.BLACK;
import util.*;

public class Player extends RegisteredEntity {

    @Override
    protected void createInner() {
        //Create the player's variables
        Signal<Vec3> position = Premade3D.makePosition(this);
        Signal<Vec3> prevPos = Premade3D.makePrevPosition(this);
        Signal<Vec3> velocity = Premade3D.makeVelocity(this);
        Mutable<Integer> ammoCount = new Mutable(3);
        Mutable<Double> moveSpeed = new Mutable(8.);

        position.set(WORLD_SIZE.multiply(.5));

        //Make the camera automatically follow the player
        position.doForEach(v -> Window3D.pos = v.add(new Vec3(0, 0, .8)));

        //Make the player collide with the floor
        Signal<CollisionInfo> collisions = Premade3D.makeCollisions(this, new Vec3(.3, .3, .9));
        Signal<Boolean> onGround = addChild(Core.update.map(() -> velocity.get().z <= 0 && CubeMap.isSolid(position.get().add(new Vec3(0, 0, -.01)), new Vec3(.3, .3, .9))));

        //Give the player basic first-person controls
        Premade3D.makeMouseLook(this, 2, -1.5, 1.5);
        Premade3D.makeWASDMovement(this, moveSpeed, onGround.map(b -> b ? .0001 : .1));
        Premade3D.makeGravity(this, new Vec3(0, 0, -15));

        //Force the player to stay inside the room
        position.filter(p -> !p.containedBy(new Vec3(0), WORLD_SIZE)).forEach(p -> {
            position.set(p.clamp(new Vec3(0), WORLD_SIZE.subtract(new Vec3(.0001))));
        });

        //Jumping
        add(Input.whileKey(Keyboard.KEY_SPACE, true).filter(onGround).onEvent(() -> {
            velocity.edit(v -> v.withZ(8));
        }));

        //Wall Jumping
        add(Input.whenKey(KEY_SPACE, true).onEvent(() -> {
            if (collisions.get() != null) {
                if (collisions.get().hitX || collisions.get().hitY) {
                    if (velocity.get().z > 0) {
                        if (!onGround.get()) {
                            velocity.edit(v -> v.add(collisions.get().normal().withLength(8)).withZ(8));
                            //Window3D.facing = Window3D.facing.withT(velocity.get().direction());
                        }
                    }
                }
            }
        }));

        //Gathering ammo
        add(Input.whenMouse(1, true).limit(.75).onEvent(() -> {
            if (ammoCount.o <= 2) {
                moveSpeed.o = moveSpeed.o * .5;
                Core.timer(.75, () -> {
                    ammoCount.o++;
                    moveSpeed.o = moveSpeed.o / .5;
                });
            }
        }));

        //Draw ammo
        Core.renderLayer(100).onEvent(() -> {
            Window3D.guiProjection();

            Graphics2D.fillRect(new Vec2(800, 50), new Vec2(300, 100), Color4.gray(.5));
            Graphics2D.drawRect(new Vec2(800, 50), new Vec2(300, 100), BLACK);
            for (int i = 0; i < ammoCount.o; i++) {
                Graphics2D.drawSprite(SpriteContainer.loadSprite("ball"), new Vec2(850 + 100 * i, 100), new Vec2(2), 0, BallAttack.BALL_COLOR);
            }

            Window3D.resetProjection();
        });

        //Throwing snowballs
        add(Input.whenMouse(0, true).limit(.5).onEvent(() -> {
            if (ammoCount.o > 0) {
                Vec3 pos = position.get().add(new Vec3(0, 0, .8));
                Vec3 vel = Window3D.facing.toVec3().withLength(30);

                Client.sendMessage(SNOWBALL, pos, vel, -1);

                BallAttack b = new BallAttack();
                b.create();
                b.get("position", Vec3.class).set(pos);
                b.get("velocity", Vec3.class).set(vel);
                ammoCount.o--;
            }
        }));
    }
}
