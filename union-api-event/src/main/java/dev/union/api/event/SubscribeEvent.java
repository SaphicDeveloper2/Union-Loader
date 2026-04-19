package dev.union.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an event handler. Discoverable by {@link EventBus#register(Object)}
 * (instance methods) and {@link EventBus#register(Class)} (static methods).
 *
 * <p>Annotated methods must:
 * <ul>
 *   <li>Be {@code public}.</li>
 *   <li>Take exactly one parameter that is a subtype of {@link Event}.</li>
 *   <li>Return {@code void}.</li>
 * </ul>
 *
 * <p>Examples:
 * <pre>
 * public class MyMod {
 *     {@literal @SubscribeEvent}
 *     public void onServerStart(ServerStartEvent e) { ... }
 *
 *     {@literal @SubscribeEvent(priority = Priority.HIGH)}
 *     public static void onRegister(RegisterEvent e) { ... }
 *
 *     {@literal @SubscribeEvent(acceptsCanceled = true)}
 *     public void onBlockBreak(BlockBreakEvent e) {
 *         // runs even if an earlier handler canceled the event
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubscribeEvent {
	Priority priority() default Priority.NORMAL;

	/** If {@code true}, this handler is invoked even when the event has been canceled. */
	boolean acceptsCanceled() default false;

	/**
	 * Named phase within this priority level. Empty string means the default phase. Phase
	 * ordering is controlled via {@link Phases#orderPhases(dev.union.api.base.Identifier, Class, String, String)}
	 * — see that class for how to declare a phase order.
	 */
	String phase() default "";
}
