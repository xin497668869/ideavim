package com.maddyhome.idea.vim.extension.around;

import com.maddyhome.idea.vim.command.MappingMode;
import com.maddyhome.idea.vim.extension.VimNonDisposableExtension;
import org.jetbrains.annotations.NotNull;

import static com.maddyhome.idea.vim.extension.VimExtensionFacade.putExtensionHandlerMapping;
import static com.maddyhome.idea.vim.extension.VimExtensionFacade.putKeyMapping;
import static com.maddyhome.idea.vim.helper.StringHelper.parseKeys;

/**
 * @author linxixin@cvte.com
 * @since 1.0
 */
public class VimSelectAroundExtension extends VimNonDisposableExtension {
  @Override
  protected void initOnce() {

    putExtensionHandlerMapping(MappingMode.ALL, parseKeys("<Plug>around"), new AroundHandler(), false);
    putExtensionHandlerMapping(MappingMode.ALL, parseKeys("<Plug>unAround"), new UnAroungHandler(), false);

    putKeyMapping(MappingMode.ALL, parseKeys("<a-w>"), parseKeys("<Plug>around"), true);
    putKeyMapping(MappingMode.ALL, parseKeys("<c-a-w>"), parseKeys("<Plug>unAround"), true);
  }

  @NotNull
  @Override
  public String getName() {
    return "around";
  }
}
