/*
 * Copyright (c) 2003-2011, Simon Brown
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *   - Neither the name of Pebble nor the names of its contributors may
 *     be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.pebble.permalink;

import net.sourceforge.pebble.api.permalink.PermalinkProvider;
import net.sourceforge.pebble.domain.*;
import net.sourceforge.pebble.index.BlogEntryIndex;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Generates permalinks based upon the blog entry title. This implementation
 * retains characters from the latin1 character by converting
 * them to suitable "url-friendly" counterparts.
 * <p/>
 * It also uses dashes instead of underscores for whitespace as this is
 * what Google recommends.
 * <p/>
 * For titles without characters from the latin1 character set
 * the blog entry ID is used for the permalink instead.
 *
 * @author Mattias Reichel
 */
public class Latin1SeoPermalinkProvider implements PermalinkProvider {

  @Inject
  private BlogService blogService;
  @Inject
  private BlogEntryIndex blogEntryIndex;

  /**
   * the regex used to check for a day request
   */
  private static final String DAY_PERMALINK_REGEX = "/\\d\\d\\d\\d/\\d\\d/\\d\\d";

  /**
   * the regex used to check for a monthly blog request
   */
  private static final String MONTH_PERMALINK_REGEX = "/\\d\\d\\d\\d/\\d\\d";

  /**
   * the regex used to check for a blog entry permalink
   */
  private static final String BLOG_ENTRY_PERMALINK_REGEX = "/[\\w-]*";

  /**
   * the Blog associated with this provider instance
   */
  private Blog blog;

  /**
   * Gets the blog associated with this provider instance.
   *
   * @return a Blog instance
   */
  public Blog getBlog() {
    return this.blog;
  }

  /**
   * Sets the blog associated with this provider instance.
   *
   * @param blog a Blog instance
   */
  public void setBlog(Blog blog) {
    this.blog = blog;
  }

  /**
   * Gets the permalink for a blog entry.
   *
   * @return a URI as a String
   */
  public synchronized String getPermalink(BlogEntry blogEntry) {
    if (blogEntry.getTitle() == null || blogEntry.getTitle().length() == 0) {
      return buildPermalink(blogEntry);
    } else {
      int count = 0;
      try {
        List<BlogEntry> entries = blogService.getBlogEntries(getBlog());
        for (int i = entries.size() - 1; i >= 0 && !entries.get(i).getId().equals(blogEntry.getId()); i--) {
          if (entries.get(i).getTitle().equals(blogEntry.getTitle())) {
            count++;
          }
        }
      } catch (BlogServiceException e) {
        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
      }

      if (count == 0) {
        return buildPermalink(blogEntry);
      } else {
        return buildPermalink(blogEntry) + "_" + blogEntry.getId();
      }
    }
  }

  private String buildPermalink(BlogEntry blogEntry) {
    String title = blogEntry.getTitle();
    if (title == null || title.length() == 0) {
      title = "" + blogEntry.getId();
    } else {
      title = title.toLowerCase();
      title = title.replaceAll("[\\. ,;/\\\\_]", "-"); // Change whitespace and punctuation marks to dashes
      for (String search : characterSubstitutions.keySet()) {
        title = title.replaceAll(search, characterSubstitutions.get(search));
      }
      title = title.replaceAll("[^a-z0-9-]", "");
      title = title.replaceAll("-+", "-");
      title = title.replaceAll("^-*", "");
      title = title.replaceAll("-*$", "");
    }

    // if the title has been blanked out, use the blog entry instead
    if (title == null || title.length() == 0) {
      title = "" + blogEntry.getId();
    }

    return "/" + title;
  }


  public boolean isBlogEntryPermalink(String uri) {
    if (uri != null) {
      return uri.matches(BLOG_ENTRY_PERMALINK_REGEX);
    } else {
      return false;
    }
  }

  public BlogEntry getBlogEntry(String uri) {
    try {
      for (BlogEntry blogEntry : blogService.getBlogEntries(getBlog())) {
        // use the local permalink, just in case the entry has been aggregated
        // and an original permalink assigned
        if (blogEntry.getLocalPermalink().endsWith(uri)) {
          return blogEntry;
        }
      }
    } catch (BlogServiceException e) {
      // do nothing
    }
    return null;
  }

  /**
   * Gets the permalink for a monthly blog.
   *
   * @param month a Month instance
   * @return a URI as a String
   */
  public String getPermalink(Month month) {
    SimpleDateFormat format = new SimpleDateFormat("yyyy'/'MM'.html'");
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.YEAR, month.getYear());
    cal.set(Calendar.MONTH, month.getMonth() - 1);
    return format.format(cal.getTime());
  }

  /**
   * Determines whether the specified URI is a monthly blog permalink.
   *
   * @param uri a relative URI
   * @return true if the URI represents a permalink to a monthly blog,
   *         false otherwise
   */
  public boolean isMonthPermalink(String uri) {
    if (uri != null) {
      return uri.matches(MONTH_PERMALINK_REGEX);
    } else {
      return false;
    }
  }

  /**
   * Gets the monthly blog referred to by the specified URI.
   *
   * @param uri a relative URI
   * @return a Month instance, or null if one can't be found
   */
  public Month getMonth(String uri) {
    String year = uri.substring(1, 5);
    String month = uri.substring(6, 8);

    return blogEntryIndex.getBlogForYear(getBlog(), Integer.parseInt(year)).getMonth(Integer.parseInt(month));
  }

  /**
   * Gets the permalink for a day.
   *
   * @param day a Day instance
   * @return a URI as a String
   */
  public String getPermalink(Day day) {
    SimpleDateFormat format = new SimpleDateFormat("yyyy'/'MM'/'dd'.html'");
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.YEAR, day.getYear());
    cal.set(Calendar.MONTH, day.getMonth() - 1);
    cal.set(Calendar.DAY_OF_MONTH, day.getDay());
    return format.format(cal.getTime());
  }

  /**
   * Determines whether the specified URI is a day permalink.
   *
   * @param uri a relative URI
   * @return true if the URI represents a permalink to a day,
   *         false otherwise
   */
  public boolean isDayPermalink(String uri) {
    if (uri != null) {
      return uri.matches(DAY_PERMALINK_REGEX);
    } else {
      return false;
    }
  }

  /**
   * Gets the day referred to by the specified URI.
   *
   * @param uri a relative URI
   * @return a Day instance, or null if one can't be found
   */
  public Day getDay(String uri) {
    String year = uri.substring(1, 5);
    String month = uri.substring(6, 8);
    String day = uri.substring(9, 11);

    return blogEntryIndex.getBlogForYear(blog, Integer.parseInt(year))
        .getMonth(Integer.parseInt(month))
        .getDay(Integer.parseInt(day));
  }


  /**
   * the List of characters that will be substituted
   */
  private static final HashMap<String, String> characterSubstitutions;

  static {

    characterSubstitutions = new HashMap<String, String>();

    characterSubstitutions.put("\u00B2", "2");
    characterSubstitutions.put("\u00B3", "3");

    characterSubstitutions.put("\u00C0", "A");
    characterSubstitutions.put("\u00C1", "A");
    characterSubstitutions.put("\u00C2", "A");
    characterSubstitutions.put("\u00C3", "A");
    characterSubstitutions.put("\u00C4", "A");
    characterSubstitutions.put("\u00C5", "A");
    characterSubstitutions.put("\u00C6", "AE");
    characterSubstitutions.put("\u00C7", "C");
    characterSubstitutions.put("\u00C8", "E");
    characterSubstitutions.put("\u00C9", "E");
    characterSubstitutions.put("\u00CA", "E");
    characterSubstitutions.put("\u00CB", "E");
    characterSubstitutions.put("\u00CC", "I");
    characterSubstitutions.put("\u00CD", "I");
    characterSubstitutions.put("\u00CE", "I");
    characterSubstitutions.put("\u00CF", "I");

    characterSubstitutions.put("\u00D0", "D");
    characterSubstitutions.put("\u00D1", "N");
    characterSubstitutions.put("\u00D2", "O");
    characterSubstitutions.put("\u00D3", "O");
    characterSubstitutions.put("\u00D4", "O");
    characterSubstitutions.put("\u00D5", "O");
    characterSubstitutions.put("\u00D6", "O");
    characterSubstitutions.put("\u00D7", "x");
    characterSubstitutions.put("\u00D8", "O");
    characterSubstitutions.put("\u00D9", "U");
    characterSubstitutions.put("\u00DA", "U");
    characterSubstitutions.put("\u00DB", "U");
    characterSubstitutions.put("\u00DC", "U");
    characterSubstitutions.put("\u00DD", "Y");
    characterSubstitutions.put("\u00DE", "P");
    characterSubstitutions.put("\u00DF", "ss");

    characterSubstitutions.put("\u00E0", "a");
    characterSubstitutions.put("\u00E1", "a");
    characterSubstitutions.put("\u00E2", "a");
    characterSubstitutions.put("\u00E3", "a");
    characterSubstitutions.put("\u00E4", "a");
    characterSubstitutions.put("\u00E5", "a");
    characterSubstitutions.put("\u00E6", "ae");
    characterSubstitutions.put("\u00E7", "c");
    characterSubstitutions.put("\u00E8", "e");
    characterSubstitutions.put("\u00E9", "e");
    characterSubstitutions.put("\u00EA", "e");
    characterSubstitutions.put("\u00EB", "e");
    characterSubstitutions.put("\u00EC", "i");
    characterSubstitutions.put("\u00ED", "i");
    characterSubstitutions.put("\u00EE", "i");
    characterSubstitutions.put("\u00EF", "i");

    characterSubstitutions.put("\u00F0", "d");
    characterSubstitutions.put("\u00F1", "n");
    characterSubstitutions.put("\u00F2", "o");
    characterSubstitutions.put("\u00F3", "o");
    characterSubstitutions.put("\u00F4", "o");
    characterSubstitutions.put("\u00F5", "o");
    characterSubstitutions.put("\u00F6", "o");
    //"\u00F7", // division sign (ignore)
    characterSubstitutions.put("\u00F8", "o");
    characterSubstitutions.put("\u00F9", "u");
    characterSubstitutions.put("\u00FA", "u");
    characterSubstitutions.put("\u00FB", "u");
    characterSubstitutions.put("\u00FC", "u");
    characterSubstitutions.put("\u00FD", "y");
    characterSubstitutions.put("\u00FE", "p");
    characterSubstitutions.put("\u00FF", "y");
  }
}
